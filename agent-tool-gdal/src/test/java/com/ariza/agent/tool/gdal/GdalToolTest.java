package com.ariza.agent.tool.gdal;

import com.ariza.agent.core.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GdalToolTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String IMAGE = "ghcr.io/osgeo/gdal:ubuntu-small-latest";

    @TempDir
    Path tempDir;

    @Test
    void exposesToolContract() {
        GdalTool tool = new GdalTool();
        assertEquals("gdal", tool.name());
        assertFalse(tool.description().isBlank());
        assertEquals("object", tool.inputSchema().get("type").asText());
        assertTrue(tool.inputSchema().get("required").toString().contains("command"));
    }

    @Test
    void rejectsBlankImage() {
        assertThrows(IllegalArgumentException.class, () -> new GdalTool("  "));
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new GdalTool(IMAGE, 0));
        assertThrows(IllegalArgumentException.class, () -> new GdalTool(IMAGE, -1));
    }

    @Test
    void rejectsNonObjectArguments() {
        ToolResult result = new GdalTool().call(OBJECT_MAPPER.getNodeFactory().textNode("x"), null);
        assertFalse(result.success());
        assertNotNull(result.error());
    }

    @Test
    void rejectsMissingCommandArgument() {
        ToolResult result = new GdalTool().call(OBJECT_MAPPER.createObjectNode(), null);
        assertFalse(result.success());
    }

    @Test
    void rejectsIllegalCommandWithoutDocker() {
        ToolResult result = new GdalTool().run("rm -rf /data");
        assertFalse(result.success());
        assertTrue(result.error().contains("不合法"));
    }

    @Test
    void rejectsRelativeMountPath() {
        ToolResult result = new GdalTool().run("gdalinfo /data/x.tif", "relative/dir");
        assertFalse(result.success());
        assertTrue(result.error().contains("绝对路径"));
    }

    @Test
    void runsGdalInfoInDockerContainer() {
        assumeDockerReady();
        ToolResult result = new GdalTool(IMAGE).run("gdalinfo --version");
        assertTrue(result.success(), () -> "stdout=" + result.output() + ", stderr=" + result.error());
        assertTrue(result.output().get("stdout").asText().contains("GDAL"));
        assertEquals(0, result.output().get("exitCode").asInt());
    }

    @Test
    void readsRasterMetadataFromMountedDirectory() {
        assumeDockerReady();
        GdalTool tool = new GdalTool(IMAGE);
        ToolResult created = tool.run(
                "gdal_create -of GTiff -outsize 100 100 -bands 1 -ot Byte /data/test.tif",
                tempDir.toString());
        assertTrue(created.success(), () -> "stdout=" + created.output() + ", stderr=" + created.error());
        assertTrue(Path.of(tempDir.toString(), "test.tif").toFile().exists());

        ToolResult info = tool.run("gdalinfo /data/test.tif", tempDir.toString());
        assertTrue(info.success(), () -> "stdout=" + info.output() + ", stderr=" + info.error());
        assertTrue(info.output().get("stdout").asText().contains("Size is 100, 100"));
    }

    @Test
    void reportsNonZeroExitCode() {
        assumeDockerReady();
        ToolResult result = new GdalTool(IMAGE).run("gdalinfo /data/not-exists.tif");
        assertFalse(result.success());
        assertEquals(1, result.output().get("exitCode").asInt());
        assertFalse(result.output().get("stderr").asText().isEmpty());
    }

    @Test
    void timesOutOnLongRunningCommand() {
        assumeDockerReady();
        GdalTool tool = new GdalTool(IMAGE, 1);
        ToolResult created = tool.run(
                "gdal_create -of GTiff -outsize 2000 2000 -bands 1 -ot Byte -a_srs EPSG:3857 -a_ullr 0 2000 2000 0 /data/a.tif",
                tempDir.toString());
        assertTrue(created.success(), () -> "stdout=" + created.output() + ", stderr=" + created.error());

        ToolResult result = tool.run("gdalwarp -ts 20000 20000 -r bilinear /data/a.tif /data/warped.tif",
                tempDir.toString());
        assertFalse(result.success());
        assertTrue(result.error().contains("超时"));
    }

    private void assumeDockerReady() {
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect", IMAGE).start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            Assumptions.assumeTrue(finished && process.exitValue() == 0,
                    "镜像 " + IMAGE + " 不存在或 Docker 不可用，跳过集成测试");
        } catch (IOException e) {
            Assumptions.abort("Docker 不可用，跳过集成测试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assumptions.abort("等待 Docker 检查被中断");
        }
    }
}