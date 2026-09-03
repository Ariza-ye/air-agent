package com.ariza.agent.anthropic;

import com.ariza.agent.core.AgentRunException;
import com.ariza.agent.core.RunContext;
import com.ariza.agent.core.model.*;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ariza
 */
class AnthropicModelClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesMessagesAndToolsAndParsesContentBlocks() throws Exception {
        String responseBody = """
                {
                  "id": "msg_123",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {"type": "text", "text": "我来查询。"},
                    {
                      "type": "tool_use",
                      "id": "toolu_123",
                      "name": "get_weather",
                      "input": {"location": "Singapore"}
                    }
                  ],
                  "stop_reason": "tool_use",
                  "usage": {
                    "input_tokens": 30,
                    "cache_creation_input_tokens": 5,
                    "cache_read_input_tokens": 9,
                    "output_tokens": 12,
                    "output_tokens_details": {"thinking_tokens": 4}
                  }
                }
                """;
        StubHttpClient httpClient = new StubHttpClient(200, responseBody);

        ModelResponse response = client(httpClient).call(new ModelRequest(
                "claude-sonnet-4-20250514",
                "You are helpful.",
                "新加坡天气如何？",
                List.of(weatherTool()),
                null));

        JsonNode request = objectMapper.readTree(httpClient.requestBody());
        assertEquals("claude-sonnet-4-20250514", request.path("model").asText());
        assertEquals(2048, request.path("max_tokens").asInt());
        assertEquals("You are helpful.", request.path("system").asText());
        assertEquals("user", request.path("messages").get(0).path("role").asText());
        assertEquals("新加坡天气如何？", request.path("messages").get(0).path("content").asText());
        assertEquals("get_weather", request.path("tools").get(0).path("name").asText());
        assertEquals("object", request.path("tools").get(0).path("input_schema").path("type").asText());
        assertEquals("test-key", httpClient.header("x-api-key"));
        assertEquals("2023-06-01", httpClient.header("anthropic-version"));

        assertEquals("我来查询。", response.text());
        assertEquals(1, response.toolCalls().size());
        assertEquals("toolu_123", response.toolCalls().get(0).callId());
        assertEquals("toolu_123", response.toolCalls().get(0).itemId());
        assertEquals("get_weather", response.toolCalls().get(0).name());
        assertEquals("Singapore", response.toolCalls().get(0).arguments().path("location").asText());
        assertEquals(ModelStatus.COMPLETED, response.status());
        assertEquals(new ModelUsage(44, 12, 56, 9, 4), response.usage());

        JsonNode history = objectMapper.readTree(response.continuation().token());
        assertEquals("anthropic", response.continuation().provider());
        assertEquals(2, history.size());
        assertEquals("assistant", history.get(1).path("role").asText());
        assertEquals("tool_use", history.get(1).path("content").get(1).path("type").asText());
    }

    @Test
    void restoresHistoryAndGroupsParallelToolResults() throws Exception {
        String history = """
                [
                  {"role":"user","content":"比较两个城市"},
                  {"role":"assistant","content":[
                    {"type":"tool_use","id":"toolu_1","name":"get_weather","input":{"location":"Singapore"}},
                    {"type":"tool_use","id":"toolu_2","name":"get_weather","input":{"location":"Tokyo"}}
                  ]}
                ]
                """;
        String responseBody = """
                {
                  "content": [{"type":"text","text":"新加坡更热。"}],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 50, "output_tokens": 8}
                }
                """;
        StubHttpClient httpClient = new StubHttpClient(200, responseBody);

        ModelResponse response = client(httpClient).call(new ModelRequest(
                "claude-sonnet-4-20250514",
                "help",
                List.of(
                        new ToolOutput("toolu_1", "{\"temperature\":30}"),
                        new ToolOutput("toolu_2", "{\"temperature\":20}")),
                List.of(weatherTool()),
                new ModelContinuation("anthropic", history)));

        JsonNode messages = objectMapper.readTree(httpClient.requestBody()).path("messages");
        assertEquals(3, messages.size());
        JsonNode results = messages.get(2).path("content");
        assertEquals("user", messages.get(2).path("role").asText());
        assertEquals(2, results.size());
        assertEquals("tool_result", results.get(0).path("type").asText());
        assertEquals("toolu_1", results.get(0).path("tool_use_id").asText());
        assertEquals("toolu_2", results.get(1).path("tool_use_id").asText());
        assertEquals("新加坡更热。", response.text());
        assertTrue(response.toolCalls().isEmpty());
    }

    @Test
    void mapsTruncationAndRefusalStatuses() {
        ModelResponse truncated = client(new StubHttpClient(200, """
                {"content":[{"type":"text","text":"未完成"}],"stop_reason":"max_tokens"}
                """)).call(new ModelRequest("claude", "help", "hello", List.of(), null));
        ModelResponse refused = client(new StubHttpClient(200, """
                {"content":[],"stop_reason":"refusal"}
                """)).call(new ModelRequest("claude", "help", "hello", List.of(), null));

        assertEquals(ModelStatus.INCOMPLETE, truncated.status());
        assertEquals("max_tokens", truncated.incompleteReason());
        assertEquals(ModelStatus.FAILED, refused.status());
        assertEquals("refusal", refused.incompleteReason());
    }

    @Test
    void rejectsInvalidContinuationAndToolInput() {
        AgentRunException providerError = assertThrows(AgentRunException.class, () ->
                client(new StubHttpClient(200, "{}")).call(new ModelRequest(
                        "claude", "help", List.of(), List.of(), new ModelContinuation("openai", "resp_1"))));
        assertEquals("不支持的 continuation provider: openai", providerError.getMessage());

        AgentRunException inputError = assertThrows(AgentRunException.class, () ->
                client(new StubHttpClient(200, """
                        {"content":[{"type":"tool_use","id":"toolu_1","name":"bad","input":"{}"}],
                         "stop_reason":"tool_use"}
                        """)).call(new ModelRequest("claude", "help", "hello", List.of(), null)));
        assertEquals("Anthropic tool_use.input 必须是 JSON 对象", inputError.getMessage());
    }

    @Test
    void reportsSafeHttpErrorsAndValidatesMaxTokens() {
        AgentRunException exception = assertThrows(AgentRunException.class, () ->
                client(new StubHttpClient(400, """
                        {"type":"error","error":{"type":"invalid_request_error","message":"Invalid tool schema"},
                         "debug":"must not be exposed"}
                        """)).call(new ModelRequest("claude", "help", "hello", List.of(), null)));

        assertEquals("Anthropic API 请求失败，HTTP 400：Invalid tool schema", exception.getMessage());
        assertFalse(exception.getMessage().contains("debug"));
        assertThrows(IllegalArgumentException.class, () -> new AnthropicModelClient("test-key", 0));
    }

    @Test
    void restoresInterruptFlagWhenRequestIsInterrupted() {
        StubHttpClient httpClient = new StubHttpClient(200, "{}") {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request,
                                            HttpResponse.BodyHandler<T> responseBodyHandler)
                    throws InterruptedException {
                throw new InterruptedException("interrupted");
            }
        };

        try {
            AgentRunException exception = assertThrows(AgentRunException.class, () ->
                    client(httpClient).call(new ModelRequest("claude", "help", "hello", List.of(), null)));
            assertEquals("Anthropic API 请求被中断", exception.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * 创建使用测试传输层的 Anthropic 客户端。
     *
     * @param httpClient 测试 HTTP 客户端
     * @return 待测试客户端
     */
    private AnthropicModelClient client(HttpClient httpClient) {
        return new AnthropicModelClient(
                "test-key",
                URI.create("https://example.test/v1/messages"),
                2048,
                httpClient,
                objectMapper);
    }

    /**
     * 创建测试使用的天气工具。
     *
     * @return 天气工具
     */
    private Tool weatherTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("location").put("type", "string");
        schema.putArray("required").add("location");
        return new Tool() {
            @Override
            public String name() {
                return "get_weather";
            }

            @Override
            public String description() {
                return "查询指定地点的天气";
            }

            @Override
            public JsonNode inputSchema() {
                return schema;
            }

            @Override
            public ToolResult call(JsonNode arguments, RunContext context) {
                throw new UnsupportedOperationException("测试不会执行工具");
            }
        };
    }

    /**
     * 记录请求并返回预设响应的测试 HTTP 客户端。
     *
     * @author Ariza
     */
    private static class StubHttpClient extends HttpClient {
        private final int statusCode;
        private final String responseBody;
        private final AtomicReference<String> requestBody = new AtomicReference<>();
        private final AtomicReference<HttpRequest> request = new AtomicReference<>();

        private StubHttpClient(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        /**
         * 读取请求发布器生成的 UTF-8 body。
         *
         * @param request HTTP 请求
         * @return 请求正文
         */
        private static String readRequestBody(HttpRequest request) throws IOException {
            HttpRequest.BodyPublisher publisher = request.bodyPublisher()
                    .orElseThrow(() -> new IOException("请求没有 body"));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CompletableFuture<Void> completion = new CompletableFuture<>();
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    output.writeBytes(bytes);
                }

                @Override
                public void onError(Throwable throwable) {
                    completion.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    completion.complete(null);
                }
            });
            try {
                completion.join();
            } catch (CompletionException e) {
                throw new IOException("读取请求 body 失败", e.getCause());
            }
            return output.toString(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request,
                                        HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            this.request.set(request);
            requestBody.set(readRequestBody(request));
            return (HttpResponse<T>) new StubHttpResponse(request, statusCode, responseBody);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        private String requestBody() {
            return requestBody.get();
        }

        private String header(String name) {
            return request.get().headers().firstValue(name).orElse(null);
        }
    }

    /**
     * 测试 HTTP 响应。
     *
     * @author Ariza
     */
    private record StubHttpResponse(HttpRequest request,
                                    int statusCode,
                                    String body) implements HttpResponse<String> {
        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("Content-Type", List.of("application/json")),
                    (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
