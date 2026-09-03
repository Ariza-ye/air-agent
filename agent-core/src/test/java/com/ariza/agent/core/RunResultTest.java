package com.ariza.agent.core;

import com.ariza.agent.core.model.ModelUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ariza
 */
class RunResultTest {

    @Test
    void appendsTextToFinalOutputWithoutChangingMetadata() {
        ModelUsage usage = new ModelUsage(10, 5, 15, 2, 1);
        RunResult result = new RunResult("回答", "run-1", "session-1", 2, usage);

        RunResult appended = result.appendFinalOutput("补充信息");

        assertNotSame(result, appended);
        assertEquals("回答补充信息", appended.finalOutput());
        assertEquals(result.runId(), appended.runId());
        assertEquals(result.sessionId(), appended.sessionId());
        assertEquals(result.turns(), appended.turns());
        assertEquals(result.usage(), appended.usage());
    }

    @Test
    void rejectsNullAdditionalOutput() {
        RunResult result = new RunResult("回答", "run-1", null, 1);

        assertThrows(NullPointerException.class, () -> result.appendFinalOutput(null));
    }
}
