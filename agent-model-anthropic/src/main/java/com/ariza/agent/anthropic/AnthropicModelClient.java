package com.ariza.agent.anthropic;

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
 * 使用 Anthropic Messages API 的模型客户端。
 *
 * <p>负责在统一模型契约与 Anthropic 的消息内容块之间转换，并通过消息历史
 * continuation 支持文本生成及客户端工具调用。</p>
 *
 * @author Ariza
 */
public final class AnthropicModelClient implements ModelClient {

    public static final int DEFAULT_MAX_TOKENS = 4096;

    private static final String PROVIDER = "anthropic";
    private static final String API_VERSION = "2023-06-01";
    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");

    private final String apiKey;
    private final URI endpoint;
    private final int maxTokens;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 使用默认 API 地址、最大输出 token 数和标准 HTTP 组件创建客户端。
     *
     * @param apiKey Anthropic API 密钥
     */
    public AnthropicModelClient(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT, DEFAULT_MAX_TOKENS, HttpClient.newHttpClient(), new ObjectMapper());
    }

    /**
     * 使用默认 API 地址和指定最大输出 token 数创建客户端。
     *
     * @param apiKey    Anthropic API 密钥
     * @param maxTokens 每次请求允许生成的最大 token 数
     */
    public AnthropicModelClient(String apiKey, int maxTokens) {
        this(apiKey, DEFAULT_ENDPOINT, maxTokens, HttpClient.newHttpClient(), new ObjectMapper());
    }

    /**
     * 使用指定 API 地址和默认最大输出 token 数创建客户端。
     *
     * @param apiKey   Anthropic API 密钥
     * @param endpoint Anthropic Messages API 地址
     */
    public AnthropicModelClient(String apiKey, URI endpoint) {
        this(apiKey,
                Objects.isNull(endpoint) ? DEFAULT_ENDPOINT : endpoint,
                DEFAULT_MAX_TOKENS,
                HttpClient.newHttpClient(),
                new ObjectMapper());
    }

    /**
     * 使用指定 API 地址和最大输出 token 数创建客户端。
     *
     * @param apiKey    Anthropic API 密钥
     * @param endpoint  Anthropic Messages API 地址
     * @param maxTokens 每次请求允许生成的最大 token 数
     */
    public AnthropicModelClient(String apiKey, URI endpoint, int maxTokens) {
        this(apiKey,
                Objects.isNull(endpoint) ? DEFAULT_ENDPOINT : endpoint,
                maxTokens,
                HttpClient.newHttpClient(),
                new ObjectMapper());
    }

    /**
     * 使用指定连接参数、默认最大输出 token 数和序列化组件创建客户端。
     *
     * @param apiKey       Anthropic API 密钥
     * @param endpoint     Anthropic Messages API 地址
     * @param httpClient   发送 HTTP 请求的客户端
     * @param objectMapper 序列化请求和解析响应的 JSON 映射器
     */
    public AnthropicModelClient(String apiKey,
                                URI endpoint,
                                HttpClient httpClient,
                                ObjectMapper objectMapper) {
        this(apiKey, endpoint, DEFAULT_MAX_TOKENS, httpClient, objectMapper);
    }

    /**
     * 使用指定连接参数和序列化组件创建客户端。
     *
     * @param apiKey       Anthropic API 密钥
     * @param endpoint     Anthropic Messages API 地址
     * @param maxTokens    每次请求允许生成的最大 token 数
     * @param httpClient   发送 HTTP 请求的客户端
     * @param objectMapper 序列化请求和解析响应的 JSON 映射器
     */
    public AnthropicModelClient(String apiKey,
                                URI endpoint,
                                int maxTokens,
                                HttpClient httpClient,
                                ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        this.apiKey = apiKey;
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.maxTokens = maxTokens;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 读取 JSON 对象中的必填非空文本字段。
     *
     * @param node         包含目标字段的 JSON 节点
     * @param field        字段名称
     * @param errorMessage 字段缺失或为空时的异常消息
     * @return 字段文本值
     */
    private static String requiredText(JsonNode node, String field, String errorMessage) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new AgentRunException(errorMessage);
        }
        return value.asText();
    }

    /**
     * 调用 Anthropic Messages API，并解析文本、工具调用、状态和 token 用量。
     *
     * @param request 通用模型请求
     * @return 规范化后的模型响应
     */
    @Override
    public ModelResponse call(ModelRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            ObjectNode body = createRequestBody(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AgentRunException(formatHttpError(response));
            }
            return parseResponse(objectMapper.readTree(response.body()), (ArrayNode) body.path("messages"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentRunException("Anthropic API 请求被中断", e);
        } catch (AgentRunException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRunException("Anthropic API 请求失败", e);
        }
    }

    /**
     * 将通用模型请求转换为 Anthropic Messages API 请求体。
     *
     * @param request 通用模型请求
     * @return 可直接序列化发送的请求体
     */
    private ObjectNode createRequestBody(ModelRequest request) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("model", request.model())
                .put("max_tokens", maxTokens)
                .put("system", request.instructions());
        ArrayNode messages = body.putArray("messages");
        restoreContinuation(request.continuation(), messages);
        appendInputItems(request.input(), messages);

        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (Tool tool : request.tools()) {
                tools.add(serializeTool(tool));
            }
        }
        return body;
    }

    /**
     * 从 continuation 恢复此前的 Anthropic 消息历史。
     *
     * @param continuation 上一次响应生成的续传信息
     * @param messages     接收历史消息的请求数组
     */
    private void restoreContinuation(ModelContinuation continuation, ArrayNode messages) {
        if (continuation == null) {
            return;
        }
        if (!PROVIDER.equals(continuation.provider())) {
            throw new AgentRunException("不支持的 continuation provider: " + continuation.provider());
        }
        if (continuation.token() == null || continuation.token().isBlank()) {
            throw new AgentRunException("Anthropic continuation token 不能为空");
        }

        JsonNode history;
        try {
            history = objectMapper.readTree(continuation.token());
        } catch (Exception e) {
            throw new AgentRunException("Anthropic continuation token 不是合法 JSON", e);
        }
        if (history == null || !history.isArray()) {
            throw new AgentRunException("Anthropic continuation token 必须是消息数组");
        }
        for (JsonNode message : history) {
            validateHistoryMessage(message);
            messages.add(message.deepCopy());
        }
    }

    /**
     * 校验 continuation 中的消息具有 Anthropic 支持的基本结构。
     *
     * @param message 待校验的历史消息
     */
    private void validateHistoryMessage(JsonNode message) {
        if (!message.isObject()) {
            throw new AgentRunException("Anthropic continuation token 包含无效消息");
        }
        String role = message.path("role").asText();
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new AgentRunException("Anthropic continuation token 包含无效消息角色");
        }
        JsonNode content = message.path("content");
        if (!content.isTextual() && !content.isArray()) {
            throw new AgentRunException("Anthropic continuation token 包含无效消息内容");
        }
    }

    /**
     * 追加本轮输入，并把连续工具结果合并到同一条 user 消息中。
     *
     * @param input    通用模型输入项
     * @param messages 接收 Anthropic 消息的数组
     */
    private void appendInputItems(List<ModelInputItem> input, ArrayNode messages) {
        for (int index = 0; index < input.size(); index++) {
            ModelInputItem item = Objects.requireNonNull(input.get(index), "model input item");
            if (item instanceof ToolOutput) {
                ObjectNode message = objectMapper.createObjectNode().put("role", "user");
                ArrayNode content = message.putArray("content");
                while (index < input.size() && input.get(index) instanceof ToolOutput toolOutput) {
                    content.add(serializeToolOutput(toolOutput));
                    index++;
                }
                index--;
                messages.add(message);
            } else if (item instanceof UserInput userInput) {
                messages.add(objectMapper.createObjectNode()
                        .put("role", "user")
                        .put("content", Objects.requireNonNull(userInput.text(), "user input text")));
            } else if (item instanceof AIInput assistantInput) {
                messages.add(objectMapper.createObjectNode()
                        .put("role", "assistant")
                        .put("content", Objects.requireNonNull(assistantInput.text(), "assistant input text")));
            } else {
                throw new AgentRunException("不支持的模型输入类型: " + item.getClass().getName());
            }
        }
    }

    /**
     * 将统一工具结果转换为 Anthropic tool_result 内容块。
     *
     * @param toolOutput 工具执行结果
     * @return Anthropic 内容块
     */
    private ObjectNode serializeToolOutput(ToolOutput toolOutput) {
        return objectMapper.createObjectNode()
                .put("type", "tool_result")
                .put("tool_use_id", Objects.requireNonNull(toolOutput.callId(), "tool output callId"))
                .put("content", Objects.requireNonNull(toolOutput.output(), "tool output"));
    }

    /**
     * 将统一工具定义转换为 Anthropic 客户端工具定义。
     *
     * @param tool 要提供给模型调用的工具
     * @return Anthropic 工具对象
     */
    private ObjectNode serializeTool(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        ObjectNode result = objectMapper.createObjectNode()
                .put("name", Objects.requireNonNull(tool.name(), "tool name"))
                .put("description", Objects.requireNonNull(tool.description(), "tool description"));
        result.set("input_schema", Objects.requireNonNull(tool.inputSchema(), "tool inputSchema"));
        return result;
    }

    /**
     * 将 Anthropic 响应转换为统一模型响应，并保存下一轮所需的消息历史。
     *
     * @param json            Anthropic 返回的 JSON 对象
     * @param requestMessages 本次发送的消息数组
     * @return 规范化后的模型响应
     */
    private ModelResponse parseResponse(JsonNode json, ArrayNode requestMessages) {
        if (json == null || !json.isObject()) {
            throw new AgentRunException("Anthropic API 返回了无效的 JSON 对象");
        }
        JsonNode content = json.path("content");
        if (!content.isArray()) {
            throw new AgentRunException("Anthropic API 响应缺少 content 数组");
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode block : content) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                JsonNode textNode = block.path("text");
                if (!textNode.isTextual()) {
                    throw new AgentRunException("Anthropic text 内容块缺少 text");
                }
                text.append(textNode.asText());
            } else if ("tool_use".equals(type)) {
                toolCalls.add(parseToolCall(block));
            }
        }

        String stopReason = requiredText(json, "stop_reason", "Anthropic API 响应缺少 stop_reason");
        ModelStatus status = parseStatus(stopReason);
        String incompleteReason = status == ModelStatus.COMPLETED ? null : stopReason;

        ArrayNode history = objectMapper.createArrayNode();
        for (JsonNode requestMessage : requestMessages) {
            history.add(requestMessage.deepCopy());
        }
        history.add(objectMapper.createObjectNode()
                .put("role", "assistant")
                .set("content", content.deepCopy()));

        return new ModelResponse(
                text.toString(),
                toolCalls,
                new ModelContinuation(PROVIDER, history.toString()),
                status,
                incompleteReason,
                parseUsage(json.path("usage")));
    }

    /**
     * 解析 Anthropic tool_use 内容块。
     *
     * @param block tool_use 内容块
     * @return 统一工具调用
     */
    private ToolCall parseToolCall(JsonNode block) {
        String id = requiredText(block, "id", "Anthropic tool_use 缺少 id");
        JsonNode input = block.path("input");
        if (!input.isObject()) {
            throw new AgentRunException("Anthropic tool_use.input 必须是 JSON 对象");
        }
        return new ToolCall(
                id,
                id,
                requiredText(block, "name", "Anthropic tool_use 缺少 name"),
                input.deepCopy());
    }

    /**
     * 解析 Anthropic token 用量，并纳入缓存创建及读取 token。
     *
     * @param usage 响应中的 usage 节点
     * @return 统一 token 用量
     */
    private ModelUsage parseUsage(JsonNode usage) {
        if (usage.isMissingNode() || usage.isNull()) {
            return ModelUsage.zero();
        }
        if (!usage.isObject()) {
            throw new AgentRunException("Anthropic API 响应的 usage 必须是对象");
        }
        long uncachedInput = requiredNonNegativeLong(usage, "input_tokens");
        long cacheCreation = optionalNonNegativeLong(usage, "cache_creation_input_tokens");
        long cacheRead = optionalNonNegativeLong(usage, "cache_read_input_tokens");
        long output = requiredNonNegativeLong(usage, "output_tokens");
        long reasoning = optionalNonNegativeLong(usage.path("output_tokens_details"), "thinking_tokens");
        try {
            long input = Math.addExact(Math.addExact(uncachedInput, cacheCreation), cacheRead);
            return new ModelUsage(input, output, Math.addExact(input, output), cacheRead, reasoning);
        } catch (ArithmeticException e) {
            throw new AgentRunException("Anthropic API 响应的 usage 数值溢出", e);
        }
    }

    /**
     * 将 Anthropic stop_reason 映射为统一模型状态。
     *
     * @param stopReason Anthropic 停止生成的原因
     * @return 对应的统一模型状态
     */
    private ModelStatus parseStatus(String stopReason) {
        return switch (stopReason) {
            case "end_turn", "tool_use", "stop_sequence" -> ModelStatus.COMPLETED;
            case "max_tokens", "model_context_window_exceeded", "pause_turn" -> ModelStatus.INCOMPLETE;
            case "refusal" -> ModelStatus.FAILED;
            default -> throw new AgentRunException("不支持的 Anthropic stop_reason: " + stopReason);
        };
    }

    /**
     * 读取必填的非负长整数字段。
     *
     * @param node  包含计数字段的节点
     * @param field 字段名称
     * @return 非负计数值
     */
    private long requiredNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new AgentRunException("Anthropic API 响应的 usage." + field + " 必须是非负整数");
        }
        return value.longValue();
    }

    /**
     * 读取可选的非负长整数字段。
     *
     * @param node  包含计数字段的节点
     * @param field 字段名称
     * @return 字段不存在时返回零，否则返回字段值
     */
    private long optionalNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new AgentRunException("Anthropic API 响应的 usage 明细必须是非负整数");
        }
        return value.longValue();
    }

    /**
     * 将非成功 HTTP 响应格式化为安全、可读的异常消息。
     *
     * @param response Anthropic 返回的非 2xx 响应
     * @return 包含 HTTP 状态码及标准错误说明的消息
     */
    private String formatHttpError(HttpResponse<String> response) {
        String message = "";
        try {
            message = objectMapper.readTree(response.body()).path("error").path("message").asText();
        } catch (Exception ignored) {
            // 非 JSON 错误响应只返回状态码，避免把完整响应带入异常。
        }
        String suffix = message.isBlank() ? "" : "：" + message;
        return "Anthropic API 请求失败，HTTP " + response.statusCode() + suffix;
    }
}
