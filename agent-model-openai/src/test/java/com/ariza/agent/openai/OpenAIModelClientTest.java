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
    void serializesUserInputAndToolsAndParsesFunctionCalls() throws Exception {
        String responseBody = """
                {
                  "id": "resp_123",
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "正在查询。"}
                      ]
                    },
                    {
                      "type": "function_call",
                      "id": "fc_123",
                      "call_id": "call_123",
                      "name": "get_weather",
                      "arguments": "{\\\"location\\\":\\\"Singapore\\\"}"
                    }
                  ],
                  "usage": {
                    "input_tokens": 20,
                    "output_tokens": 8,
                    "total_tokens": 28,
                    "input_tokens_details": {"cached_tokens": 5},
                    "output_tokens_details": {"reasoning_tokens": 3}
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
        assertEquals("You are helpful.", request.path("instructions").asText());
        assertEquals("user", request.path("input").get(0).path("role").asText());
        assertEquals("新加坡天气如何？", request.path("input").get(0).path("content").asText());
        assertEquals("function", request.path("tools").get(0).path("type").asText());
        assertEquals("get_weather", request.path("tools").get(0).path("name").asText());
        assertEquals("object", request.path("tools").get(0).path("parameters").path("type").asText());
        assertEquals("Bearer test-key", httpClient.authorization());

        assertEquals("正在查询。", response.text());
        assertEquals(1, response.toolCalls().size());
        assertEquals("call_123", response.toolCalls().get(0).callId());
        assertEquals("fc_123", response.toolCalls().get(0).itemId());
        assertEquals("get_weather", response.toolCalls().get(0).name());
        assertEquals("Singapore", response.toolCalls().get(0).arguments().path("location").asText());
        assertEquals(new ModelContinuation("openai", "resp_123"), response.continuation());
        assertEquals(ModelStatus.COMPLETED, response.status());
        assertEquals(new ModelUsage(20, 8, 28, 5, 3), response.usage());
    }

    @Test
    void serializesToolOutputWithPreviousResponseId() throws Exception {
        String responseBody = """
                {
                  "id": "resp_456",
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "新加坡现在是 30°C。"}
                      ]
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
                new ModelContinuation("openai", "resp_123")
        ));

        JsonNode request = objectMapper.readTree(httpClient.requestBody());
        JsonNode toolOutput = request.path("input").get(0);
        assertEquals("resp_123", request.path("previous_response_id").asText());
        assertEquals("function_call_output", toolOutput.path("type").asText());
        assertEquals("call_123", toolOutput.path("call_id").asText());
        assertEquals("{\"temperature\":30}", toolOutput.path("output").asText());
        assertEquals("新加坡现在是 30°C。", response.text());
        assertTrue(response.toolCalls().isEmpty());
    }

    @Test
    void mapsIncompleteStatusAndReason() throws Exception {
        String responseBody = """
                {
                  "id": "resp_incomplete",
                  "status": "incomplete",
                  "incomplete_details": {"reason": "max_output_tokens"},
                  "output": []
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
        assertEquals("max_output_tokens", response.incompleteReason());
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
                URI.create("https://example.test/v1/responses"),
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
