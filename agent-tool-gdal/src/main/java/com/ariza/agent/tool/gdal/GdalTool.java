package com.ariza.agent.tool.gdal;

import com.ariza.agent.core.RunContext;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在 GDAL Docker 运行环境中执行 GDAL/OGR 命令行并返回日志的工具。
 *
 * <p>执行不经过任何 shell：命令由 {@link GdalCommandParser} 解析为参数数组后，
 * 通过 {@code docker run} 以 argv 形式直接传给容器内的 GDAL 工具。使用前需先按模块
 * 内 {@code Dockerfile} 构建镜像。</p>
 *
 * @author ariza
 */
public class GdalTool implements Tool {

    /**
     * 默认 GDAL 运行镜像名称，即官方 GDAL 镜像。
     */
    public static final String DEFAULT_IMAGE = "ghcr.io/osgeo/gdal:ubuntu-small-latest";

    /**
     * 容器内数据挂载目录，命令中通过 {@code /data/...} 引用用户文件。
     */
    public static final String DATA_DIR = "/data";

    private static final long DEFAULT_TIMEOUT_SECONDS = 120L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String image;
    private final long timeoutSeconds;

    /**
     * 使用默认镜像与默认超时创建工具。
     */
    public GdalTool() {
        this(DEFAULT_IMAGE, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 使用指定镜像创建工具。
     *
     * @param image GDAL 运行镜像名称，例如 {@code gdal-agent:latest}
     */
    public GdalTool(String image) {
        this(image, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 使用指定镜像和超时创建工具。
     *
     * @param image          GDAL 运行镜像名称
     * @param timeoutSeconds 单条命令执行超时秒数，必须大于 0
     */
    public GdalTool(String image, long timeoutSeconds) {
        this.image = Objects.requireNonNull(image, "image").trim();
        if (this.image.isEmpty()) {
            throw new IllegalArgumentException("镜像名称不能为空");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("超时秒数必须大于 0");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String name() {
        return "gdal";
    }

    @Override
    public String description() {
        return "在 GDAL Docker 运行环境中执行 GDAL/OGR 命令行并返回日志。"
                + "命令必须以白名单内的 GDAL 工具开头，例如 gdalinfo、gdal_translate、gdalwarp、ogr2ogr、ogrinfo 等。"
                + "容器内数据目录为 /data，可通过 mount 参数把宿主目录挂载到 /data，"
                + "命令中通过 /data/xxx 引用该目录下的文件。";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("command")
                .put("type", "string")
                .put("description", "要执行的完整 GDAL/OGR 命令行，例如 gdalinfo /data/sample.tif");
        properties.putObject("mount")
                .put("type", "string")
                .put("description", "可选；宿主目录绝对路径，挂载到容器内 /data，命令中通过 /data/xxx 引用文件");
        schema.putArray("required").add("command");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments, RunContext context) {
        if (arguments == null || !arguments.isObject()) {
            return ToolResult.failure("工具参数必须是 JSON 对象");
        }
        JsonNode commandNode = arguments.get("command");
        if (commandNode == null || !commandNode.isTextual() || commandNode.asText().isBlank()) {
            return ToolResult.failure("缺少必填参数: command");
        }
        JsonNode mountNode = arguments.get("mount");
        String mount = mountNode == null || mountNode.isNull() ? null : mountNode.asText();
        return run(commandNode.asText(), mount);
    }

    /**
     * 执行一条 GDAL 命令并返回日志。
     *
     * @param command 完整的 GDAL 命令行，例如 {@code gdalinfo /data/sample.tif}
     * @return 命令执行结果；解析校验失败、执行超时或 Docker 不可用时返回失败结果
     */
    public ToolResult run(String command) {
        return run(command, null);
    }

    /**
     * 执行一条 GDAL 命令，并将宿主目录挂载到容器内 {@value #DATA_DIR} 后返回日志。
     *
     * @param command 完整的 GDAL 命令行
     * @param mount   宿主目录绝对路径；可为 {@code null} 表示不挂载
     * @return 命令执行结果
     */
    public ToolResult run(String command, String mount) {
        GdalCommand parsed = GdalCommandParser.parse(command);
        if (parsed == null) {
            return ToolResult.failure("命令不合法：必须以白名单内 GDAL 工具开头，且参数不能包含 shell 元字符");
        }
        List<String> argv = new ArrayList<>(List.of("docker", "run", "--rm"));
        String containerName = "gdal-tool-" + UUID.randomUUID();
        argv.add("--name");
        argv.add(containerName);
        if (mount != null && !mount.isBlank()) {
            Path path = Path.of(mount);
            if (!path.isAbsolute()) {
                return ToolResult.failure("mount 必须是宿主目录的绝对路径: " + mount);
            }
            argv.add("-v");
            argv.add(mount + ":" + DATA_DIR);
        }
        argv.add(image);
        argv.add(parsed.tool());
        argv.addAll(parsed.args());
        return execute(argv, command, containerName);
    }

    /**
     * 以 argv 形式启动进程执行命令，收集输出日志并返回结果。
     *
     * @param argv          完整进程参数，不经过 shell
     * @param command       原始命令行，用于回显
     * @param containerName 本次执行使用的 Docker 容器名
     * @return 执行结果；输出日志包含 stdout 与 stderr
     */
    private ToolResult execute(List<String> argv, String command, String containerName) {
        Process process;
        try {
            process = new ProcessBuilder(argv).start();
        } catch (IOException e) {
            return ToolResult.failure("无法启动 Docker 进程: " + e.getMessage());
        }

        AtomicReference<String> stdout = new AtomicReference<>();
        AtomicReference<String> stderr = new AtomicReference<>();
        Thread stdoutReader = readAsync(process.getInputStream(), stdout);
        Thread stderrReader = readAsync(process.getErrorStream(), stderr);
        stdoutReader.start();
        stderrReader.start();

        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                terminateContainer(containerName);
                process.destroyForcibly();
                return ToolResult.failure("命令执行超时(" + timeoutSeconds + "s): " + command);
            }
        } catch (InterruptedException e) {
            terminateContainer(containerName);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return ToolResult.failure("命令执行被中断: " + command);
        }
        joinQuietly(stdoutReader);
        joinQuietly(stderrReader);

        int exitCode = process.exitValue();
        ObjectNode output = objectMapper.createObjectNode()
                .put("command", command)
                .put("exitCode", exitCode)
                .put("stdout", stdout.get())
                .put("stderr", stderr.get());
        if (exitCode == 0) {
            return ToolResult.success(output);
        }
        return new ToolResult(false, output, "命令执行失败，退出码 " + exitCode);
    }

    /**
     * 创建读取进程输出流的后台线程。
     */
    private Thread readAsync(java.io.InputStream stream, AtomicReference<String> target) {
        return new Thread(() -> {
            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
            target.set(content.toString());
        });
    }

    /**
     * 等待输出读取线程结束，忽略中断。
     */
    private void joinQuietly(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 停止超时或中断后遗留的 Docker 容器，避免孤儿容器残留。
     */
    private void terminateContainer(String containerName) {
        try {
            Process stop = new ProcessBuilder("docker", "stop", "-t", "0", containerName).start();
            stop.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}