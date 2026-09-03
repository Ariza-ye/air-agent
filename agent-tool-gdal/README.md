# agent-tool-gdal

在 Docker 容器中执行 GDAL/OGR 命令并返回日志的 Agent 工具。模块本身不依赖本机安装的
GDAL，命令运行在官方 GDAL 镜像内，具备完整的驱动支持（PNG、JPEG、JP2OpenJPEG 等）。

该模块是可选模块，用户按需引用，**不包含在** `agent-spring-boot-starter` 中。

## 特性

- 直接实现 `com.ariza.agent.core.tool.Tool` 接口，只依赖 `agent-core`。
- 命令以 argv 数组形式直接传给容器内的 GDAL 工具，**不经过任何 shell**，杜绝 shell 注入。
- 工具名白名单校验：仅允许执行 GDAL/OGR 标准命令行工具。
- 可选 `mount` 参数把宿主目录挂载到容器 `/data`，命令内通过 `/data/xxx` 引用文件。
- 支持超时控制，超时或中断后自动 `docker stop` 清理容器，不残留孤儿容器。
- 返回执行日志：`exitCode`、`stdout`、`stderr`。

## 环境要求

- 已安装并可用 Docker。
- 能访问 `ghcr.io`（官方 GDAL 镜像仓库）。

## 构建运行镜像

先从模块目录下的 `Dockerfile` 构建镜像：

```bash
docker build -t gdal-agent:latest agent-tool-gdal
```

也可以跳过构建，直接使用官方镜像 `ghcr.io/osgeo/gdal:ubuntu-small-latest`
（`GdalTool` 的默认镜像），前提是宿主机能拉取该镜像。

## Maven 依赖

```xml

<dependency>
    <groupId>com.ariza.agent</groupId>
    <artifactId>agent-tool-gdal</artifactId>
    <version>0.1.1-SNAPSHOT</version>
</dependency>
```

## 快速使用

注册工具到 Agent：

```java
import com.ariza.agent.core.Agent;
import com.ariza.agent.tool.gdal.GdalTool;

var agent = Agent.builder()
        .name("GIS assistant")
        .instructions("使用 gdal 工具检查地理空间文件，文件位于 /data 目录")
        .model("gpt-4.1-mini")
        .tools(List.of(new GdalTool()))
        .build();
```

### run 方法

`GdalTool` 提供 `run` 方法，可直接在业务代码中调用：

```java
GdalTool tool = new GdalTool();

// 不挂载目录，仅查询版本
ToolResult version = tool.run("gdalinfo --version");

// 挂载宿主目录 /absolute/path/to/host/dir 到容器 /data
ToolResult info = tool.run("gdalinfo /data/sample.tif", "/absolute/path/to/host/dir");
```

也支持指定镜像与超时：

```java
// 使用指定镜像，单条命令超时 300 秒
new GdalTool("ghcr.io/osgeo/gdal:ubuntu-full-latest",300);
```

### 工具参数

模型调用时传入 JSON 对象：

| 参数        | 类型     | 必填 | 说明                                             |
|-----------|--------|----|------------------------------------------------|
| `command` | string | 是  | 完整 GDAL/OGR 命令行，例如 `gdalinfo /data/sample.tif` |
| `mount`   | string | 否  | 宿主目录绝对路径，挂载到容器 `/data`，命令中通过 `/data/xxx` 引用    |

### 返回结果

```json
{
  "command": "gdalinfo /data/sample.tif",
  "exitCode": 0,
  "stdout": "...",
  "stderr": ""
}
```

- `exitCode == 0` 时结果为成功；
- 非 0 退出码视为失败，`stderr` 中保留错误日志；
- 命令不合法、执行超时或 Docker 不可用时返回失败结果。

## 支持的命令

白名单包含 GDAL/OGR 全部标准命令行工具，常用命令包括：

`gdalinfo`、`gdal_translate`、`gdalwarp`、`gdaladdo`、`gdalbuildvrt`、
`gdaldem`、`gdal_calc.py`、`gdal_merge.py`、`gdal2tiles.py`、`ogrinfo`、`ogr2ogr`、
`ogrinfo`、`ogrmerge` 等。

`gdal_calc.py` 等工具的参数表达式（例如 `--calc="A*(B>0)"`）会被原样保留。

## 安全说明

- 命令第一个 token 必须是白名单内的 GDAL/OGR 工具，其他命令一律拒绝。
- 参数中不允许出现 `;`、`|`、`&`、`$`、反引号、换行等 shell 元字符。
- 命令通过 `docker run` 以 argv 形式直接执行，不经过 shell。
- 超时后自动执行 `docker stop` 清理容器，避免孤儿容器残留。
- 请谨慎控制 `mount` 挂载的宿主目录，容器内命令对该目录拥有读写权限。

## 测试

```bash
mvn -pl agent-tool-gdal -am test
```

集成测试依赖本机 Docker 与官方 GDAL 镜像；镜像不存在或 Docker 不可用时自动跳过。