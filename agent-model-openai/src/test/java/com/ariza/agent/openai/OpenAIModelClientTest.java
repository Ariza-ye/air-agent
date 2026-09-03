package com.ariza.agent.openai;

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
 * @author ariza
 */
class OpenAIModelClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesMessagesAndToolsAndParsesToolCalls() throws Exception {
        String responseBody = """
                {
                  "id": "chatcmpl-123",
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "tool_calls",
                      "message": {
                        "role": "assistant",
                        "content": "正在查询。",
                        "tool_calls": [
                          {
                            "id": "call_123",
                            "type": "function",
                            "function": {
                              "name": "get_weather",
                              "arguments": "{\\\"location\\\":\\\"Singapore\\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 20,
                    "completion_tokens": 8,
                    "total_tokens": 28,
                    "prompt_tokens_details": {"cached_tokens": 5},
                    "completion_tokens_details": {"reasoning_tokens": 3}
                  }
                }
                """;

        StubHttpClient httpClient = new StubHttpClient(200, responseBody);
        OpenAIModelClient client = client(httpClient);
        ModelResponse response = client.call(new ModelRequest(
                "test-model",
                "You are helpful.",
                "新加坡天气如何？",
                List.of(weatherTool()),
                null
        ));

        JsonNode request = objectMapper.readTree(httpClient.requestBody());
        assertEquals("test-model", request.path("model").asText());
        assertEquals("system", request.path("messages").get(0).path("role").asText());
        assertEquals("You are helpful.", request.path("messages").get(0).path("content").asText());
        assertEquals("user", request.path("messages").get(1).path("role").asText());
        assertEquals("新加坡天气如何？", request.path("messages").get(1).path("content").asText());
        assertEquals("function", request.path("tools").get(0).path("type").asText());
        assertEquals("get_weather", request.path("tools").get(0).path("function").path("name").asText());
        assertEquals("object", request.path("tools").get(0).path("function").path("parameters").path("type").asText());
        assertEquals("Bearer test-key", httpClient.authorization());

        assertEquals("正在查询。", response.text());
        assertEquals(1, response.toolCalls().size());
        assertEquals("call_123", response.toolCalls().get(0).callId());
        assertEquals("call_123", response.toolCalls().get(0).itemId());
        assertEquals("get_weather", response.toolCalls().get(0).name());
        assertEquals("Singapore", response.toolCalls().get(0).arguments().path("location").asText());
        assertNotNull(response.continuation());
        assertEquals("openai", response.continuation().provider());
        JsonNode continuedMessages = objectMapper.readTree(response.continuation().token());
        assertEquals(3, continuedMessages.size());
        assertEquals("assistant", continuedMessages.get(2).path("role").asText());
        assertEquals("get_weather", continuedMessages.get(2).path("tool_calls").get(0).path("function").path("name").asText());
        assertEquals(ModelStatus.COMPLETED, response.status());
        assertEquals(new ModelUsage(20, 8, 28, 5, 3), response.usage());
    }

    @Test
    void serializesToolOutputIntoContinuedMessages() throws Exception {
        String continuedMessages = """
                [
                  {"role":"system","content":"You are helpful."},
                  {"role":"user","content":"新加坡天气如何？"},
                  {"role":"assistant","tool_calls":[{"id":"call_123","type":"function","function":{"name":"get_weather","arguments":"{\\\"location\\\":\\\"Singapore\\\"}"}}]}
                ]
                """;
        String responseBody = """
                {
                  "id": "chatcmpl-456",
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "stop",
                      "message": {"role": "assistant", "content": "新加坡现在是 30°C。"}
                    }
                  ]
                }
                """;

        StubHttpClient httpClient = new StubHttpClient(200, responseBody);
        OpenAIModelClient client = client(httpClient);
        ModelResponse response = client.call(new ModelRequest(
                "test-model",
                "You are helpful.",
                List.of(new ToolOutput("call_123", "{\"temperature\":30}")),
                List.of(weatherTool()),
                new ModelContinuation("openai", continuedMessages)
        ));

        JsonNode request = objectMapper.readTree(httpClient.requestBody());
        assertEquals(4, request.path("messages").size());
        JsonNode toolMessage = request.path("messages").get(3);
        assertEquals("tool", toolMessage.path("role").asText());
        assertEquals("call_123", toolMessage.path("tool_call_id").asText());
        assertEquals("{\"temperature\":30}", toolMessage.path("content").asText());
        assertEquals("新加坡现在是 30°C。", response.text());
        assertTrue(response.toolCalls().isEmpty());
        assertNull(response.continuation());
        assertEquals(ModelStatus.COMPLETED, response.status());
    }

    @Test
    void parsesTextOnlyCompletionWithoutUsage() throws Exception {
        String responseBody = """
                {
                  "id": "chatcmpl-text",
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "stop",
                      "message": {"role": "assistant", "content": "你好"}
                    }
                  ]
                }
                """;

        StubHttpClient httpClient = new StubHttpClient(200, responseBody);
        ModelResponse response = client(httpClient).call(new ModelRequest(
                "test-model",
                "help",
                "hello",
                List.of(),
                null
        ));

        assertEquals("你好", response.text());
        assertTrue(response.toolCalls().isEmpty());
        assertNull(response.continuation());
        assertEquals(ModelStatus.COMPLETED, response.status());
        assertEquals(ModelUsage.zero(), response.usage());
    }

    @Test
    void mapsIncompleteLengthStatusAndReason() throws Exception {
        String responseBody = """
                {
                  "id": "chatcmpl-len",
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "length",
                      "message": {"role": "assistant", "content": "部分输出"}
                    }
                  ]
                }
                """;

        StubHttpClient httpClient = new StubHttpClient(200, responseBody);
        ModelResponse response = client(httpClient).call(new ModelRequest(
                "test-model",
                "help",
                "hello",
                List.of(),
                null
        ));

        assertEquals(ModelStatus.INCOMPLETE, response.status());
        assertEquals("length", response.incompleteReason());
    }

    @Test
    void includesApiErrorMessageWithoutEchoingResponseBody() throws Exception {
        String responseBody = """
                {
                  "error": {
                    "message": "Invalid tool schema",
                    "type": "invalid_request_error"
                  },
                  "debug": "must not be exposed"
                }
                """;

        StubHttpClient httpClient = new StubHttpClient(400, responseBody);
        AgentRunException exception = assertThrows(
                AgentRunException.class,
                () -> client(httpClient).call(new ModelRequest(
                        "test-model",
                        "help",
                        "hello",
                        List.of(),
                        null
                ))
        );

        assertEquals("OpenAI API 请求失败，HTTP 400：Invalid tool schema", exception.getMessage());
    }

    private OpenAIModelClient client(HttpClient httpClient) {
        return new OpenAIModelClient(
                "test-key",
                URI.create("https://example.test/v1/chat/completions"),
                httpClient,
                objectMapper
        );
    }

    private Tool weatherTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties")
                .putObject("location")
                .put("type", "string");
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
     * @author ariza
     */
    private static final class StubHttpClient extends HttpClient {
        private final int statusCode;
        private final String responseBody;
        private final AtomicReference<String> requestBody = new AtomicReference<>();
        private final AtomicReference<String> authorization = new AtomicReference<>();

        private StubHttpClient(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

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
                                        HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            requestBody.set(readRequestBody(request));
            authorization.set(request.headers().firstValue("Authorization").orElse(null));
            return (HttpResponse<T>) new StubHttpResponse(request, statusCode, responseBody);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (IOException e) {
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

        private String authorization() {
            return authorization.get();
        }
    }

    /**
     * @author ariza
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