package com.ariza.agent.tool.reflect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ariza
 */
class ToolResultFieldTest {

    @Test
    void exposesMetadataAtRuntimeOnSupportedElements() throws Exception {
        Field field = SampleResult.class.getDeclaredField("title");
        ToolResultField fieldMetadata = field.getAnnotation(ToolResultField.class);
        assertNotNull(fieldMetadata);
        assertEquals("新闻标题", fieldMetadata.description());

        RecordComponent component = News.class.getRecordComponents()[0];
        ToolResultField componentMetadata = component.getAnnotation(ToolResultField.class);
        assertNotNull(componentMetadata);
        assertEquals("新闻发布时间", componentMetadata.description());
        assertFalse(componentMetadata.hasValue());
        assertEquals(ToolFieldFormat.DATE_TIME, componentMetadata.format());
        assertEquals("date-time", componentMetadata.format().value());
        assertEquals("LocalDateTime、OffsetDateTime", componentMetadata.format().javaType());
        assertEquals("2026-08-07T10:30:00+08:00", componentMetadata.format().example());

        ToolResultField importanceMetadata = News.class.getRecordComponents()[1].getAnnotation(ToolResultField.class);
        assertNotNull(importanceMetadata);
        assertArrayEquals(new String[]{"LOW", "MEDIUM", "HIGH"}, importanceMetadata.allowedValues());

        Method getter = SampleResult.class.getDeclaredMethod("getSource");
        assertNotNull(getter.getAnnotation(ToolResultField.class));
    }

    /**
     * @author ariza
     */
    private static final class SampleResult {
        @ToolResultField(description = "新闻标题")
        private String title;

        @ToolResultField(description = "新闻来源")
        String getSource() {
            return "示例新闻社";
        }
    }

    /**
     * @author ariza
     */
    private record News(
            @ToolResultField(
                    description = "新闻发布时间",
                    hasValue = false,
                    format = ToolFieldFormat.DATE_TIME
            )
            String publishedAt,
            @ToolResultField(
                    description = "新闻重要程度",
                    allowedValues = {"LOW", "MEDIUM", "HIGH"}
            )
            String importance
    ) {
    }
}
