package com.ariza.agent.core.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 字段对应：
 *
 * @param callId    ← OpenAI call_id
 * @param itemId    ← OpenAI id
 * @param name      ← OpenAI name
 * @param arguments ← 解析 OpenAI JSON 字符串 arguments
 * @author ariza
 */
public record ToolCall(String callId,
                       String itemId,
                       String name,
                       JsonNode arguments) {
}
