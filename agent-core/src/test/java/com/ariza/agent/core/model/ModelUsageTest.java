package com.ariza.agent.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author ariza
 */
class ModelUsageTest {

    @Test
    void addsEveryUsageField() {
        ModelUsage usage = new ModelUsage(10, 4, 14, 3, 2)
                .add(new ModelUsage(8, 6, 14, 5, 1));

        assertEquals(new ModelUsage(18, 10, 28, 8, 3), usage);
    }

    @Test
    void rejectsNegativeTokenCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelUsage(-1, 0, 0, 0, 0));
    }
}
