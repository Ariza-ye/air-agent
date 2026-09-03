package com.ariza.agent.tool.reflect;

import com.ariza.agent.core.RunContext;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ariza
 */
class ReflectionToolFactoryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReflectionToolFactory factory = new ReflectionToolFactory(objectMapper);

    @Test
    void createsToolDefinitionAndInvokesAnnotatedMethod() {
        Tool tool = factory.create(new SampleTools()).get(0);

        assertEquals("find_weather", tool.name());
        assertEquals("查询天气", tool.description());
        assertEquals("object", tool.inputSchema().path("type").asText());
        assertEquals("string", tool.inputSchema().path("properties").path("city").path("type").asText());
        assertEquals("integer", tool.inputSchema().path("properties").path("days").path("type").asText());
        assertEquals(List.of("city"), objectMapper.convertValue(
                tool.inputSchema().path("required"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        ));
        assertFalse(tool.inputSchema().path("additionalProperties").asBoolean());

        RunContext context = new RunContext();
        ObjectNode arguments = objectMapper.createObjectNode()
                .put("city", "Singapore")
                .put("days", 2);
        ToolResult result = tool.call(arguments, context);

        assertTrue(result.success());
        assertEquals("Singapore:2", result.output().asText());
        assertEquals("Singapore", context.attributes().get("city"));
    }

    @Test
    void reportsMissingRequiredAndConversionErrorsAsToolFailures() {
        Tool tool = factory.create(new SampleTools()).get(0);

        ToolResult missing = tool.call(objectMapper.createObjectNode(), new RunContext());
        ToolResult invalid = tool.call(
                objectMapper.createObjectNode().put("city", "Singapore").put("days", "many"),
                new RunContext()
        );

        assertFalse(missing.success());
        assertEquals("缺少必填参数: city", missing.error());
        assertFalse(invalid.success());
    }

    @Test
    void returnsToolResultAndUnwrapsMethodException() {
        List<Tool> tools = factory.create(new ResultTools());

        ToolResult direct = tools.stream()
                .filter(tool -> tool.name().equals("direct"))
                .findFirst()
                .orElseThrow()
                .call(objectMapper.createObjectNode(), new RunContext());
        ToolResult failed = tools.stream()
                .filter(tool -> tool.name().equals("explode"))
                .findFirst()
                .orElseThrow()
                .call(objectMapper.createObjectNode(), new RunContext());

        assertTrue(direct.success());
        assertEquals("ok", direct.output().asText());
        assertFalse(failed.success());
        assertEquals("boom", failed.error());
    }

    @Test
    void addsAnnotatedResultFieldsToToolDescription() throws Exception {
        Tool tool = factory.create(new NewsTools()).get(0);

        String prefix = "\n返回值结构（JSON Schema）：\n";
        assertTrue(tool.description().startsWith("查询新闻" + prefix));
        JsonNode schema = objectMapper.readTree(tool.description().substring(
                tool.description().indexOf(prefix) + prefix.length()
        ));

        JsonNode itemSchema = schema.path("items");
        assertEquals("array", schema.path("type").asText());
        assertEquals("object", itemSchema.path("type").asText());
        assertEquals("新闻标题", itemSchema.path("properties").path("title").path("description").asText());
        assertEquals("date-time", itemSchema.path("properties").path("publishedAt").path("format").asText());
        assertEquals(
                "2026-08-07T10:30:00+08:00",
                itemSchema.path("properties").path("publishedAt").path("example").asText()
        );
        assertEquals(
                List.of("LOW", "MEDIUM", "HIGH"),
                objectMapper.convertValue(
                        itemSchema.path("properties").path("importance").path("enum"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                )
        );
        assertEquals(
                List.of("title"),
                objectMapper.convertValue(
                        itemSchema.path("required"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                )
        );
    }

    @Test
    void rejectsInvalidDeclarations() {
        assertThrows(NullPointerException.class, () -> factory.create(null));
        assertThrows(IllegalArgumentException.class, () -> factory.create(new MissingParamAnnotation()));
        assertThrows(IllegalArgumentException.class, () -> factory.create(new DuplicateNames()));
        assertThrows(IllegalArgumentException.class, () -> factory.create(new OptionalPrimitive()));
    }

    /**
     * @author ariza
     */
    private static final class SampleTools {
        @AgentTool(name = "find_weather", description = "查询天气")
        private String weather(
                @ToolParam(value = "city", description = "城市") String city,
                @ToolParam(value = "days", description = "天数", required = false) Integer days,
                RunContext context) {
            context.attributes().put("city", city);
            return city + ":" + days;
        }
    }

    /**
     * @author ariza
     */
    private static final class ResultTools {
        @AgentTool(description = "直接返回结果")
        ToolResult direct() {
            return ToolResult.success(JsonNodeFactory.instance.textNode("ok"));
        }

        @AgentTool(description = "抛出异常")
        void explode() {
            throw new IllegalStateException("boom");
        }
    }

    /**
     * @author ariza
     */
    private static final class NewsTools {
        @AgentTool(description = "查询新闻")
        List<NewsResult> news() {
            return List.of();
        }
    }

    /**
     * @author ariza
     */
    private static final class NewsResult {
        @ToolResultField(description = "新闻标题", hasValue = true)
        private String title;

        private String publishedAt;

        @ToolResultField(
                description = "新闻重要程度",
                allowedValues = {"LOW", "MEDIUM", "HIGH"}
        )
        private String importance;

        public String getTitle() {
            return title;
        }

        @ToolResultField(description = "新闻发布时间", format = ToolFieldFormat.DATE_TIME)
        public String getPublishedAt() {
            return publishedAt;
        }

        public String getImportance() {
            return importance;
        }
    }

    /**
     * @author ariza
     */
    private static final class MissingParamAnnotation {
        @AgentTool(description = "无参数注解")
        String invalid(String value) {
            return value;
        }
    }

    /**
     * @author ariza
     */
    private static final class DuplicateNames {
        @AgentTool(name = "same", description = "一")
        void first() {
        }

        @AgentTool(name = "same", description = "二")
        void second() {
        }
    }

    /**
     * @author ariza
     */
    private static final class OptionalPrimitive {
        @AgentTool(description = "错误的可选基本类型")
        int invalid(@ToolParam(value = "value", required = false) int value) {
            return value;
        }
    }
}
