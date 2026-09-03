package com.ariza.agent.core.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ariza
 */
class ModelResponseTest {

    @Test
    void createsCompletedTextResponse() {
        ModelResponse response = ModelResponse.text("ok");

        assertEquals("ok", response.text());
        assertEquals(List.of(), response.toolCalls());
        assertNull(response.continuation());
        assertEquals(ModelStatus.COMPLETED, response.status());
        assertNull(response.incompleteReason());
        assertEquals(ModelUsage.zero(), response.usage());
        assertFalse(response.hasToolCalls());
    }

    @Test
    void createsCompletedResponseWithToolCalls() {
        ToolCall toolCall = new ToolCall("call-1", "item-1", "test", null);

        ModelResponse response = new ModelResponse("ok", List.of(toolCall));

        assertEquals("ok", response.text());
        assertEquals(List.of(toolCall), response.toolCalls());
        assertNull(response.continuation());
        assertEquals(ModelStatus.COMPLETED, response.status());
        assertNull(response.incompleteReason());
        assertTrue(response.hasToolCalls());
    }

    @Test
    void preservesContinuationStatusAndIncompleteReason() {
        ModelContinuation continuation = new ModelContinuation("openai", "response-1");

        ModelResponse response = new ModelResponse(
                "partial",
                List.of(),
                continuation,
                ModelStatus.INCOMPLETE,
                "max_output_tokens");

        assertEquals("partial", response.text());
        assertEquals(continuation, response.continuation());
        assertEquals(ModelStatus.INCOMPLETE, response.status());
        assertEquals("max_output_tokens", response.incompleteReason());
    }

    @Test
    void normalizesNullTextAndToolCalls() {
        ModelResponse response = new ModelResponse(null, null);

        assertEquals("", response.text());
        assertEquals(List.of(), response.toolCalls());
        assertEquals(ModelStatus.COMPLETED, response.status());
    }

    @Test
    void defaultsNullStatusToCompleted() {
        ModelResponse response = new ModelResponse("", List.of(), null, null, null, null);

        assertEquals(ModelStatus.COMPLETED, response.status());
        assertEquals(ModelUsage.zero(), response.usage());
    }

    @Test
    void copiesToolCallsToImmutableList() {
        List<ToolCall> toolCalls = new ArrayList<>();
        ModelResponse response = new ModelResponse("", toolCalls);

        toolCalls.add(new ToolCall("call-1", "item-1", "test", null));

        assertEquals(List.of(), response.toolCalls());
        assertThrows(UnsupportedOperationException.class,
                () -> response.toolCalls().add(new ToolCall("call-2", "item-2", "test", null)));
    }
}
