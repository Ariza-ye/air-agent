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
     * 使用 OpenAI Chat Completions API 默认地址和标准 HTTP 组件创建模型客户端。
     *
     * @param apiKey 用于调用 OpenAI API 的密钥
     */
    public OpenAIModelClient(String apiKey) {
        this(apiKey, URI.create("https://api.openai.com/v1/chat/completions"), HttpClient.newHttpClient(), new ObjectMapper());
    }

    /**
     * 使用指定 API 地址和标准 HTTP 组件创建模型客户端。
     *
     * @param apiKey   用于调用模型 API 的密钥
     * @param endpoint 模型 API 地址
     */
    public OpenAIModelClient(String apiKey, URI endpoint) {
        this(apiKey, Objects.isNull(endpoint) ? URI.create("https://api.openai.com/v1/chat/completions") : endpoint, HttpClient.newHttpClient(), new ObjectMapper());
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
     * 调用 OpenAI Chat Completions API，并解析输出文本、函数调用、续传信息及响应状态。
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
            ArrayNode requestMessages = (ArrayNode) body.get("messages");
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AgentRunException(formatHttpError(response));
            }
            return parseResponse(objectMapper.readTree(response.body()), requestMessages);
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
     * 将通用模型请求转换为 OpenAI Chat Completions API 请求体。
     *
     * <p>Chat Completions 是无状态协议，每条消息都必须完整携带。首次请求构建
     * {@code system} 指令与输入消息；续传请求从 continuation 令牌恢复已有消息
     * 并追加本轮输入（通常为工具执行结果）。</p>
     *
     * @param request 通用模型请求
     * @return 可直接发送给 OpenAI Chat Completions API 的 JSON 请求体
     * @throws AgentRunException 当续传信息不属于 OpenAI 或续传令牌无效时抛出
     */
    private ObjectNode createRequestBody(ModelRequest request) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("model", request.model());

        ArrayNode messages = objectMapper.createArrayNode();
        ModelContinuation continuation = request.continuation();
        if (continuation != null) {
            if (!PROVIDER.equals(continuation.provider())) {
                throw new AgentRunException("不支持的 continuation provider: " + continuation.provider());
            }
            if (continuation.token() == null || continuation.token().isBlank()) {
                throw new AgentRunException("OpenAI continuation token 不能为空");
            }
            try {
                JsonNode restored = objectMapper.readTree(continuation.token());
                if (restored == null || !restored.isArray()) {
                    throw new AgentRunException("OpenAI continuation 消息不是合法 JSON 数组");
                }
                messages = (ArrayNode) restored;
            } catch (AgentRunException e) {
                throw e;
            } catch (Exception e) {
                throw new AgentRunException("OpenAI continuation 消息解析失败", e);
            }
        } else {
            messages.add(message("system", request.instructions()));
        }

        for (ModelInputItem item : request.input()) {
            messages.add(serializeInputItem(item));
        }
        body.set("messages", messages);

        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (Tool tool : request.tools()) {
                tools.add(serializeTool(tool));
            }
        }

        return body;
    }

    /**
     * 将单个通用模型输入项转换为 OpenAI Chat Completions 消息。
     *
     * @param item 用户输入、AI 回复或工具执行结果
     * @return OpenAI 格式的 JSON 消息
     * @throws NullPointerException 当输入项或其必填字段为 {@code null} 时抛出
     * @throws AgentRunException    当输入项类型不受支持时抛出
     */
    private ObjectNode serializeInputItem(ModelInputItem item) {
        Objects.requireNonNull(item, "model input item");
        if (item instanceof UserInput userInput) {
            return message("user", Objects.requireNonNull(userInput.text(), "user input text"));
        }
        if (item instanceof AIInput assistantInput) {
            return message("assistant", assistantInput.text());
        }
        if (item instanceof ToolOutput toolOutput) {
            return objectMapper.createObjectNode()
                    .put("role", "tool")
                    .put("tool_call_id", Objects.requireNonNull(toolOutput.callId(), "tool output callId"))
                    .put("content", Objects.requireNonNull(toolOutput.output(), "tool output"));
        }
        throw new AgentRunException("不支持的模型输入类型: " + item.getClass().getName());
    }

    /**
     * 创建带角色和内容的普通消息。
     *
     * @param role    消息角色
     * @param content 消息内容；为空时使用空字符串
     * @return 单条 JSON 消息
     */
    private ObjectNode message(String role, String content) {
        return objectMapper.createObjectNode()
                .put("role", role)
                .put("content", content == null ? "" : content);
    }

    /**
     * 将通用工具定义转换为 OpenAI Chat Completions function 工具定义。
     *
     * @param tool 要提供给模型调用的工具
     * @return 以 {@code function} 嵌套结构表示的 OpenAI 工具对象
     * @throws NullPointerException 当工具或工具必填字段为 {@code null} 时抛出
     */
    private ObjectNode serializeTool(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        JsonNode inputSchema = Objects.requireNonNull(tool.inputSchema(), "tool inputSchema");
        ObjectNode function = objectMapper.createObjectNode()
                .put("name", Objects.requireNonNull(tool.name(), "tool name"))
                .put("description", Objects.requireNonNull(tool.description(), "tool description"))
                .set("parameters", inputSchema);
        return objectMapper.createObjectNode()
                .put("type", "function")
                .set("function", function);
    }

    /**
     * 将 OpenAI Chat Completions API 响应转换为通用模型响应。
     *
     * <p>解析内容包括输出文本、函数调用、完成原因和用于下一轮请求的续传消息。
     * 当响应包含工具调用时，会把对应的 {@code assistant} 消息追加到请求消息并
     * 序列化为续传令牌，供下一轮完整重发。</p>
     *
     * @param json            OpenAI Chat Completions API 返回的 JSON 对象
     * @param requestMessages 本轮请求携带的完整消息数组
     * @return 规范化后的模型响应
     * @throws AgentRunException 当响应结构缺失必填字段或字段格式无效时抛出
     */
    private ModelResponse parseResponse(JsonNode json, ArrayNode requestMessages) {
        if (json == null || !json.isObject()) {
            throw new AgentRunException("OpenAI API 返回了无效的 JSON 对象");
        }

        JsonNode choices = json.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AgentRunException("OpenAI API 响应缺少 choices 数组");
        }
        JsonNode message = choices.get(0).path("message");
        if (!message.isObject()) {
            throw new AgentRunException("OpenAI API 响应缺少 message 对象");
        }

        String text = message.path("content").asText();
        List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));

        ModelContinuation continuation = null;
        if (!toolCalls.isEmpty()) {
            requestMessages.add(assistantToolCallMessage(text, toolCalls));
            try {
                continuation = new ModelContinuation(PROVIDER, objectMapper.writeValueAsString(requestMessages));
            } catch (Exception e) {
                throw new AgentRunException("OpenAI continuation 序列化失败", e);
            }
        }

        String finishReason = choiceFinishReason(choices.get(0));
        ModelStatus status = parseStatus(finishReason);
        String reason = status == ModelStatus.INCOMPLETE || status == ModelStatus.FAILED
                ? finishReason
                : null;
        ModelUsage usage = parseUsage(json.path("usage"));
        return new ModelResponse(text, toolCalls, continuation, status, reason, usage);
    }

    /**
     * 读取单个 choice 的完成原因，缺失时返回空字符串。
     *
     * @param choice Chat Completions choice 节点
     * @return 完成原因文本，可能为空字符串
     */
    private String choiceFinishReason(JsonNode choice) {
        return choice.path("finish_reason").asText("");
    }

    /**
     * 解析 Chat Completions 的 usage 对象。
     *
     * @param usage Chat Completions 返回的用量节点
     * @return 规范化后的用量；缺失或为 {@code null} 时返回零用量
     * @throws AgentRunException 当 usage 不是对象或必填字段格式无效时抛出
     */
    private ModelUsage parseUsage(JsonNode usage) {
        if (usage.isMissingNode() || usage.isNull()) {
            return ModelUsage.zero();
        }
        if (!usage.isObject()) {
            throw new AgentRunException("OpenAI API 响应的 usage 必须是对象");
        }
        return new ModelUsage(
                requiredNonNegativeLong(usage, "prompt_tokens"),
                requiredNonNegativeLong(usage, "completion_tokens"),
                requiredNonNegativeLong(usage, "total_tokens"),
                optionalNonNegativeLong(usage.path("prompt_tokens_details"), "cached_tokens"),
                optionalNonNegativeLong(usage.path("completion_tokens_details"), "reasoning_tokens"));
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
     * 将 Chat Completions 的 {@code tool_calls} 数组转换为通用工具调用列表。
     *
     * @param toolCalls 模型的函数调用节点；缺失或非数组时视为空
     * @return 包含调用标识、工具名称和参数的通用工具调用列表
     * @throws AgentRunException 当必填字段缺失或 arguments 不是合法 JSON 对象时抛出
     */
    private List<ToolCall> parseToolCalls(JsonNode toolCalls) {
        List<ToolCall> result = new ArrayList<>();
        if (!toolCalls.isArray()) {
            return result;
        }
        for (JsonNode call : toolCalls) {
            if (!"function".equals(call.path("type").asText())) {
                continue;
            }
            JsonNode function = call.path("function");
            String name = function.path("name").asText();
            if (name.isBlank()) {
                throw new AgentRunException("OpenAI tool_call 缺少 function.name");
            }
            String argumentsText = function.path("arguments").asText();
            if (argumentsText.isBlank()) {
                argumentsText = "{}";
            }
            JsonNode arguments;
            try {
                arguments = objectMapper.readTree(argumentsText);
            } catch (Exception e) {
                throw new AgentRunException("OpenAI tool_call arguments 不是合法 JSON", e);
            }
            if (arguments == null || !arguments.isObject()) {
                throw new AgentRunException("OpenAI tool_call arguments 必须是 JSON 对象");
            }
            String callId = requiredText(call, "id", "OpenAI tool_call 缺少 id");
            result.add(new ToolCall(callId, callId, name, arguments));
        }
        return result;
    }

    /**
     * 为包含工具调用的响应构造 {@code assistant} 消息，用于续传时与 {@code tool}
     * 结果配对。
     *
     * @param text      模型返回的文本，可能为空
     * @param toolCalls 本轮工具调用
     * @return 带 {@code tool_calls} 数组的 assistant 消息
     */
    private ObjectNode assistantToolCallMessage(String text, List<ToolCall> toolCalls) {
        ObjectNode node = objectMapper.createObjectNode().put("role", "assistant");
        if (text != null && !text.isBlank()) {
            node.put("content", text);
        }
        ArrayNode calls = node.putArray("tool_calls");
        for (ToolCall toolCall : toolCalls) {
            ObjectNode call = calls.addObject();
            call.put("id", toolCall.callId());
            call.put("type", "function");
            ObjectNode function = call.putObject("function");
            function.put("name", toolCall.name());
            function.put("arguments", toolCall.arguments() == null ? "{}" : toolCall.arguments().toString());
        }
        return node;
    }

    /**
     * 将 Chat Completions 完成原因映射为通用模型状态。
     *
     * @param finishReason 模型返回的完成原因，可能为空字符串
     * @return 对应的通用模型状态
     */
    private ModelStatus parseStatus(String finishReason) {
        return switch (finishReason) {
            case "length" -> ModelStatus.INCOMPLETE;
            case "content_filter" -> ModelStatus.FAILED;
            default -> ModelStatus.COMPLETED;
        };
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