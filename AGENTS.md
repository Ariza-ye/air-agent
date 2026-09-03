# 仓库贡献指南

## 项目概览

本项目是基于 Java 17 和 Spring Boot 3.5.8 的 Maven 多模块 Agent Runtime，核心设计参考
[openai-agents-python](https://github.com/openai/openai-agents-python)。根目录 `pom.xml` 统一管理版本和 Reactor 构建顺序。

## 模块结构

- `agent-core`：Agent、Runner、模型请求/响应、工具、护栏、会话和任务移交等核心契约。
- `agent-model-openai`：OpenAI Responses API 适配器，支持文本和 function tool call。
- `agent-model-anthropic`：Anthropic Messages API 适配器，支持文本和工具调用。
- `agent-tool-reflect`：通过 `@AgentTool`、`@ToolParam` 和 `@ToolResultField` 把 Java 方法转换为工具。
- `agent-session`：进程内 `InMemorySessionStore`。
- `agent-tracing`：日志追踪导出器；Span 和导出接口位于 `agent-core`。
- `agent-spring-boot-starter`：OpenAI `ModelClient`、`Runner` 和 `AgentsProperties` 自动配置。
- `examples/simple-agent`：使用 OpenAI、Spring Bean 工具扫描和 HTTP 接口的可运行示例。

生产代码位于 `模块名/src/main/java/com/ariza/agents/...`，测试代码放在对应的 `src/test/java` 包路径，资源文件放在
`src/main/resources`。可复用能力应进入库模块，不要沉积在示例应用中。

## 当前运行模型

`Runner.runAgent` 首次发送用户文本；模型返回工具调用时，Runner 按名称查找 Agent 注册的 `Tool`，共享同一个
`RunContext` 执行工具，再携带 `ToolOutput` 和提供商 continuation 继续调用模型，直到返回最终文本或达到 `maxTurns`。

以下能力目前仅提供契约，尚未自动集成到 Runner：`Handoff` 和 `Guardrail`。

修改模型适配器时必须保持 `ModelRequest`/`ModelResponse` 的统一语义，并覆盖文本响应、工具调用、continuation、非 2xx、无效响应和线程中断路径。修改反射工具时，应覆盖参数 Schema、必填/可选参数、类型转换、异常结果、继承扫描、重复名称和返回字段说明。

## 构建、测试与运行

所有命令均在仓库根目录执行：

- `mvn clean verify`：编译全部模块并运行完整测试套件。
- `mvn test`：运行全部单元测试，不执行 clean。
- `mvn -pl agent-core -am test`：测试核心模块及其 Reactor 依赖。
- `mvn -pl agent-model-anthropic -am test`：测试 Anthropic 适配器及其依赖。
- `mvn -pl agent-tool-reflect -am test`：测试反射工具模块及其依赖。
- `OPENAI_API_KEY=... mvn -f examples/simple-agent/pom.xml spring-boot:run`：完成 `mvn install` 后启动示例，默认监听 8080。

示例当前用 `OPENAI_API_KEY` 为自定义的 `OpenAIModelClient` 提供密钥，并通过 `OPENAI_MODEL` 覆盖默认模型
`gpt-4.1-mini`。这是当前配置兼容方式；不要据此把 OpenAI 自动配置误写成 Starter 的默认行为。

## 编码约定

使用 UTF-8 和 4 个空格缩进。Java 类型采用 `UpperCamelCase`，方法和字段采用 `lowerCamelCase`，常量采用
`UPPER_SNAKE_CASE`；包名保持小写并置于 `com.ariza.agent` 下。遵循相邻代码风格，保持导入清晰，优先使用职责单一的类、不可变集合副本以及明确的空值和范围校验。

仓库没有强制格式化或静态检查插件。提交前使用 IDE 格式化，并执行：

```bash
mvn clean verify
git diff --check
git status --short
```

不要修改或提交 IDE 工作区文件、`target/`、本地密钥或与当前任务无关的用户改动。

## 测试规范

测试使用 JUnit 5 和 Maven Surefire。测试类命名为 `*Test`，测试方法描述可观察行为，例如
`executesToolCallsUntilModelReturnsText`。测试应放在被修改模块中并镜像生产包结构。新增分支、参数校验、协议解析和失败路径都应有测试；公共 API 或用户可见行为改变时同步更新 README 和示例。

## 提交与 Pull Request

提交标题应简短、使用祈使语气并可加模块前缀，例如 `core: handle model tool calls`。每次提交只处理一个明确主题。Pull Request 应说明问题、解决方案、受影响模块和实际验证命令，并关联相关 Issue。API 变更应提供调用示例；只有用户可见界面发生变化时才需要截图。

## 安全与配置

API Key 必须通过环境变量或安全密钥设施注入。禁止写入 `application.yml`、源码、测试、日志或提交历史。模型客户端允许传入自定义 endpoint；调用前必须确认目标可信。工具在应用进程内执行，应验证模型参数、限制权限，禁止直接执行未经校验的命令、查询或外部副作用。
