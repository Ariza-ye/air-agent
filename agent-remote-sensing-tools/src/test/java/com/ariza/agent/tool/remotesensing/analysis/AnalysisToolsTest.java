package com.ariza.agent.tool.remotesensing.analysis;

import com.ariza.agent.core.RunContext;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ariza
 */
class AnalysisToolsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesStableNamesAndObjectSchemas() {
        AnalysisExecutor executor = (operation, arguments, context) ->
                ToolResult.success(objectMapper.createObjectNode());
        List<Tool> tools = List.of(
                new RasterStatsTool(executor),
                new CalcIndexTool(executor),
                new CompareImageTool(executor),
                new ZonalStatsTool(executor));

        assertEquals(
                List.of("rasterStats", "calcIndex", "compareImage", "zonalStats"),
                tools.stream().map(Tool::name).toList());
        for (Tool tool : tools) {
            assertEquals("object", tool.inputSchema().path("type").asText());
            assertFalse(tool.inputSchema().path("additionalProperties").asBoolean());
            assertFalse(tool.inputSchema().path("required").isEmpty());
        }
    }

    @Test
    void delegatesValidatedArgumentsToExecutor() throws Exception {
        AtomicReference<String> operationRef = new AtomicReference<>();
        AtomicReference<JsonNode> argumentsRef = new AtomicReference<>();
        AnalysisExecutor executor = (operation, arguments, context) -> {
            operationRef.set(operation);
            argumentsRef.set(arguments);
            return ToolResult.success(objectMapper.createObjectNode().put("mean", 0.42));
        };
        Tool tool = new RasterStatsTool(executor);
        JsonNode arguments = objectMapper.readTree("""
                {
                  "rasterUri": "s3://imagery/scene.tif",
                  "bands": [1, 2],
                  "statistics": ["mean", "max"]
                }
                """);

        ToolResult result = tool.call(arguments, new RunContext());

        assertTrue(result.success());
        assertEquals(0.42, result.output().path("mean").doubleValue());
        assertEquals("rasterStats", operationRef.get());
        assertEquals(arguments, argumentsRef.get());
        assertNotSame(arguments, argumentsRef.get());
    }

    @Test
    void validatesRequiredArgumentsBeforeExecution() {
        AnalysisExecutor executor = (operation, arguments, context) -> {
            throw new AssertionError("无效参数不应交给执行器");
        };

        ToolResult rasterResult = new RasterStatsTool(executor)
                .call(objectMapper.createObjectNode(), new RunContext());
        ToolResult indexResult = new CalcIndexTool(executor)
                .call(objectMapper.createObjectNode()
                        .put("rasterUri", "scene.tif")
                        .put("expression", "(nir-red)/(nir+red)"), new RunContext());
        ToolResult compareResult = new CompareImageTool(executor)
                .call(objectMapper.createObjectNode().put("referenceRasterUri", "before.tif"), new RunContext());
        ToolResult zonalResult = new ZonalStatsTool(executor)
                .call(objectMapper.createObjectNode()
                        .put("rasterUri", "scene.tif")
                        .put("zonesUri", "zones.geojson"), new RunContext());

        assertEquals("rasterUri 必须是非空字符串", rasterResult.error());
        assertEquals("bandMapping 必须是非空对象", indexResult.error());
        assertEquals("targetRasterUri 必须是非空字符串", compareResult.error());
        assertEquals("zoneField 必须是非空字符串", zonalResult.error());
    }

    @Test
    void validatesBandsAndOptionalValues() {
        AnalysisExecutor executor = (operation, arguments, context) ->
                ToolResult.success(objectMapper.createObjectNode());
        ObjectNode invalidBands = objectMapper.createObjectNode().put("rasterUri", "scene.tif");
        invalidBands.putArray("bands").add(0);
        ObjectNode invalidNoData = objectMapper.createObjectNode()
                .put("rasterUri", "scene.tif")
                .put("zonesUri", "zones.geojson")
                .put("zoneField", "id")
                .put("noData", "none");

        ToolResult bandsResult = new RasterStatsTool(executor).call(invalidBands, new RunContext());
        ToolResult noDataResult = new ZonalStatsTool(executor).call(invalidNoData, new RunContext());

        assertFalse(bandsResult.success());
        assertEquals("bands 中的波段编号必须是大于等于 1 的整数", bandsResult.error());
        assertFalse(noDataResult.success());
        assertEquals("noData 必须是数值", noDataResult.error());
    }

    @Test
    void convertsExecutorFailuresToToolFailures() {
        Tool throwingTool = new RasterStatsTool((operation, arguments, context) -> {
            throw new IllegalStateException("backend unavailable");
        });
        Tool nullTool = new RasterStatsTool((operation, arguments, context) -> null);
        ObjectNode arguments = objectMapper.createObjectNode().put("rasterUri", "scene.tif");

        ToolResult throwingResult = throwingTool.call(arguments, new RunContext());
        ToolResult nullResult = nullTool.call(arguments, new RunContext());

        assertFalse(throwingResult.success());
        assertEquals("遥感分析执行失败: backend unavailable", throwingResult.error());
        assertFalse(nullResult.success());
        assertEquals("分析执行器返回了 null", nullResult.error());
    }
}
