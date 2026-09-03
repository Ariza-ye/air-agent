package com.ariza.agent.openai;

import com.ariza.agent.core.AgentRunException;
import com.ariza.agent.core.model.*;
import com.ariza.agent.core.tool.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author ariza
 */
public final class OpenAIModelClient implements ModelClient {

    private static final String PROVIDER = "openai";

    private final String apiKey;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 使用 OpenAI Responses API 默认地址和标准 HTTP 组件创建模型客户端。
     *
     * @param apiKey 用于调用 OpenAI API 的密钥
     */
    public OpenAIModelClient(String apiKey) {
        this(apiKey, URI.create("https://api.openai.com/v1/responses"), HttpClient.newHttpClient(), new ObjectMapper());
    }

    /**
     * 使用指定 API 地址和标准 HTTP 组件创建模型客户端。
     *
     * @param apiKey   用于调用模型 API 的密钥
     * @param endpoint 模型 API 地址
     */
    public OpenAIModelClient(String apiKey, URI endpoint) {
        this(apiKey, Objects.isNull(endpoint) ? URI.create("https://api.openai.com/v1/responses") : endpoint, HttpClient.newHttpClient(), new ObjectMapper());
    }

    /**
     * 使用指定连接参数和序列化组件创建模型客户端。
     *
     * @param apiKey       用于调用模型 API 的密钥
     * @param endpoint     模型 API 地址
     * @param httpClient   发送 HTTP 请求的客户端
     * @param objectMapper 序列化请求和解析响应的 JSON 映射器
     */
    public OpenAIModelClient(String apiKey, URI endpoint, HttpClient httpClient, ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.apiKey = apiKey;
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 读取 JSON 对象中的必填非空文本字段。
     *
     * @param node         包含目标字段的 JSON 节点
     * @param field        字段名称
     * @param errorMessage 字段缺失或为空时使用的异常消息
     * @return 字段文本值
     * @throws AgentRunException 当字段缺失或文本值为空时抛出
     */
    private static String requiredText(JsonNode node, String field, String errorMessage) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new AgentRunException(errorMessage);
        }
        return value;
    }

    /**
     * 调用 OpenAI Responses API，并解析输出文本、函数调用、续传信息及响应状态。
     *
     * @param request 模型名称、指令、输入及可用工具组成的请求
     * @return 规范化后的模型响应
     * @throws AgentRunException 当请求被中断、HTTP 状态异常或响应处理失败时抛出
     */
    @Override
    public ModelResponse call(ModelRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            ObjectNode body = createRequestBody(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AgentRunException(formatHttpError(response));
            }
            return parseResponse(objectMapper.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentRunException("OpenAI API 请求被中断", e);
        } catch (AgentRunException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRunException("OpenAI API 请求失败", e);
        }
    }

    /**
     * 将通用模型请求转换为 OpenAI Responses API 请求体。
     *
     * <p>该方法负责序列化本轮输入、可用工具，并在续传请求中写入
     * {@code previous_response_id}。</p>
     *
     * @param request 通用模型请求
     * @return 可直接发送给 OpenAI Responses API 的 JSON 请求体
     * @throws AgentRunException 当续传信息不属于 OpenAI 或续传令牌为空时抛出
     */
    private ObjectNode createRequestBody(ModelRequest request) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("model", request.model())
                .put("instructions", request.instructions());

        ArrayNode input = body.putArray("input");
        for (ModelInputItem item : request.input()) {
            input.add(serializeInputItem(item));
        }

        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (Tool tool : request.tools()) {
                tools.add(serializeTool(tool));
            }
        }

        ModelContinuation continuation = request.continuation();
        if (continuation != null) {
            if (!PROVIDER.equals(continuation.provider())) {
                throw new AgentRunException("不支持的 continuation provider: " + continuation.provider());
            }
            if (continuation.token() == null || continuation.token().isBlank()) {
                throw new AgentRunException("OpenAI continuation token 不能为空");
            }
            body.put("previous_response_id", continuation.token());
        }

        return body;
    }

    /**
     * 将单个通用模型输入项转换为 OpenAI Responses API 输入项。
     *
     * @param item 用户输入或工具执行结果
     * @return OpenAI 格式的 JSON 输入项
     * @throws NullPointerException 当输入项或其必填字段为 {@code null} 时抛出
     * @throws AgentRunException    当输入项类型不受支持时抛出
     */
    private ObjectNode serializeInputItem(ModelInputItem item) {
        Objects.requireNonNull(item, "model input item");
        if (item instanceof UserInput userInput) {
            return objectMapper.createObjectNode()
                    .put("role", "user")
                    .put("content", Objects.requireNonNull(userInput.text(), "user input text"));
        }
        if (item instanceof ToolOutput toolOutput) {
            return objectMapper.createObjectNode()
                    .put("type", "function_call_output")
                    .put("call_id", Objects.requireNonNull(toolOutput.callId(), "tool output callId"))
                    .put("output", Objects.requireNonNull(toolOutput.output(), "tool output"));
        }
        if (item instanceof AIInput assistantInput) {
            return objectMapper.createObjectNode()
                    .put("role", "assistant")
                    .put("content", assistantInput.text());
        }
        throw new AgentRunException("不支持的模型输入类型: " + item.getClass().getName());
    }

    /**
     * 将通用工具定义转换为 OpenAI function 工具定义。
     *
     * @param tool 要提供给模型调用的工具
     * @return 包含工具名称、说明和参数 JSON Schema 的 OpenAI 工具对象
     * @throws NullPointerException 当工具或工具必填字段为 {@code null} 时抛出
     */
    private ObjectNode serializeTool(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        JsonNode inputSchema = Objects.requireNonNull(tool.inputSchema(), "tool inputSchema");
        return objectMapper.createObjectNode()
                .put("type", "function")
                .put("name", Objects.requireNonNull(tool.name(), "tool name"))
                .put("description", Objects.requireNonNull(tool.description(), "tool description"))
                .set("parameters", inputSchema);
    }

    /**
     * 将 OpenAI Responses API 响应转换为通用模型响应。
     *
     * <p>解析内容包括输出文本、函数调用、响应状态、未完成原因和用于下一轮
     * 请求的响应标识。</p>
     *
     * @param json OpenAI Responses API 返回的 JSON 对象
     * @return 规范化后的模型响应
     * @throws AgentRunException 当响应结构缺失必填字段或字段格式无效时抛出
     */
    private ModelResponse parseResponse(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new AgentRunException("OpenAI API 返回了无效的 JSON 对象");
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode outputItems = json.path("output");
        if (!outputItems.isArray()) {
            throw new AgentRunException("OpenAI API 响应缺少 output 数组");
        }

        for (JsonNode output : outputItems) {
            String type = output.path("type").asText();
            if ("message".equals(type)) {
                appendOutputText(output, text);
            } else if ("function_call".equals(type)) {
                toolCalls.add(parseToolCall(output));
            }
        }

        String responseId = requiredText(json, "id", "OpenAI API 响应缺少 id");
        ModelContinuation continuation = new ModelContinuation(PROVIDER, responseId);
        ModelStatus status = parseStatus(requiredText(json, "status", "OpenAI API 响应缺少 status"));
        String reason = parseReason(json, status);
        ModelUsage usage = parseUsage(json.path("usage"));
        return new ModelResponse(text.toString(), toolCalls, continuation, status, reason, usage);
    }

    private ModelUsage parseUsage(JsonNode usage) {
        if (usage.isMissingNode() || usage.isNull()) {
            return ModelUsage.zero();
        }
        if (!usage.isObject()) {
            throw new AgentRunException("OpenAI API 响应的 usage 必须是对象");
        }
        return new ModelUsage(
                requiredNonNegativeLong(usage, "input_tokens"),
                requiredNonNegativeLong(usage, "output_tokens"),
                requiredNonNegativeLong(usage, "total_tokens"),
                optionalNonNegativeLong(usage.path("input_tokens_details"), "cached_tokens"),
                optionalNonNegativeLong(usage.path("output_tokens_details"), "reasoning_tokens"));
    }

    private long requiredNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new AgentRunException("OpenAI API 响应的 usage." + field + " 必须是非负整数");
        }
        return value.longValue();
    }

    private long optionalNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new AgentRunException("OpenAI API 响应的 usage 明细必须是非负整数");
        }
        return value.longValue();
    }

    /**
     * 从 message 输出项中提取所有 {@code output_text} 内容并追加到文本缓冲区。
     *
     * @param output OpenAI message 输出项
     * @param text   用于累计模型可见文本的缓冲区
     */
    private void appendOutputText(JsonNode output, StringBuilder text) {
        for (JsonNode content : output.path("content")) {
            if ("output_text".equals(content.path("type").asText())) {
                text.append(content.path("text").asText());
            }
        }
    }

    /**
     * 将 OpenAI {@code function_call} 输出项转换为通用工具调用。
     *
     * @param output OpenAI function_call 输出项
     * @return 包含调用标识、输出项标识、工具名称和参数的工具调用
     * @throws AgentRunException 当必填字段缺失或 arguments 不是合法 JSON 对象时抛出
     */
    private ToolCall parseToolCall(JsonNode output) {
        String argumentsText = requiredText(
                output,
                "arguments",
                "OpenAI function_call 缺少 arguments"
        );
        JsonNode arguments;
        try {
            arguments = objectMapper.readTree(argumentsText);
        } catch (Exception e) {
            throw new AgentRunException("OpenAI function_call arguments 不是合法 JSON", e);
        }
        if (arguments == null || !arguments.isObject()) {
            throw new AgentRunException("OpenAI function_call arguments 必须是 JSON 对象");
        }

        return new ToolCall(
                requiredText(output, "call_id", "OpenAI function_call 缺少 call_id"),
                requiredText(output, "id", "OpenAI function_call 缺少 id"),
                requiredText(output, "name", "OpenAI function_call 缺少 name"),
                arguments
        );
    }

    /**
     * 将 OpenAI 响应状态映射为通用模型状态。
     *
     * @param status OpenAI 响应状态字符串
     * @return 对应的通用模型状态
     * @throws AgentRunException 当状态不属于当前客户端支持的终态时抛出
     */
    private ModelStatus parseStatus(String status) {
        return switch (status) {
            case "completed" -> ModelStatus.COMPLETED;
            case "incomplete" -> ModelStatus.INCOMPLETE;
            case "failed", "cancelled" -> ModelStatus.FAILED;
            default -> throw new AgentRunException("不支持的 OpenAI 响应状态: " + status);
        };
    }

    /**
     * 根据模型状态提取未完成原因或失败原因。
     *
     * @param json   OpenAI Responses API 响应对象
     * @param status 已映射的通用模型状态
     * @return 未完成或失败原因；响应正常完成或没有原因时返回 {@code null}
     */
    private String parseReason(JsonNode json, ModelStatus status) {
        if (status == ModelStatus.INCOMPLETE) {
            String reason = json.path("incomplete_details").path("reason").asText();
            return reason.isBlank() ? null : reason;
        }
        if (status == ModelStatus.FAILED) {
            String message = json.path("error").path("message").asText();
            if (!message.isBlank()) {
                return message;
            }
            String code = json.path("error").path("code").asText();
            return code.isBlank() ? null : code;
        }
        return null;
    }

    /**
     * 将非成功 HTTP 响应格式化为安全、可读的异常消息。
     *
     * <p>如果响应包含标准 OpenAI error 对象，则附加其中的 message；不会直接
     * 回显完整响应体，避免泄露无关或敏感内容。</p>
     *
     * @param response OpenAI 返回的非 2xx HTTP 响应
     * @return 包含 HTTP 状态码及可用错误说明的消息
     */
    private String formatHttpError(HttpResponse<String> response) {
        String message = "";
        try {
            JsonNode error = objectMapper.readTree(response.body()).path("error");
            message = error.path("message").asText();
        } catch (Exception ignored) {
            // 非 JSON 错误响应只返回状态码，避免把整段响应内容或敏感信息带入异常。
        }
        String suffix = message.isBlank() ? "" : "：" + message;
        return "OpenAI API 请求失败，HTTP " + response.statusCode() + suffix;
    }
}
