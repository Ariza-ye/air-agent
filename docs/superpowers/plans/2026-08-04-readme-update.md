# README Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 根据当前源码和项目结构，把根目录 `README.md` 重写为同时适合 Java 开发者与项目贡献者的详细中文项目说明。

**Architecture:** README 采用双层阅读路径：先提供项目定位、环境要求和可运行的快速开始，再说明模块结构、核心契约、扩展方式和贡献规范。所有描述都从当前工作区的 POM、Java 源码、Spring Boot 配置和示例应用中核对，不把 `plan.md` 中的规划能力描述成当前已完成能力。

**Tech Stack:** Markdown、Java 21、Maven、Spring Boot 3.5.8、JUnit 5、OpenAI Responses API。

## Global Constraints

- 实施阶段只修改根目录 `README.md`，不改动用户已有的 Java 源码、配置、测试或计划文档。
- 使用中文撰写说明；Java 类型、配置键、命令、路径和代码标识符保持源码中的原始拼写。
- 不单独增加“已实现 / 未实现”能力清单。
- 不声称仓库已经支持工具调用循环、反射工具自动创建、Handoff 调度、Guardrail 执行、Session 自动接入 Runner、流式输出或完整追踪编排。
- API Key 只通过 `OPENAI_API_KEY` 环境变量提供，不建议将密钥写入源码或配置文件。
- 不写仓库无法验证的发布坐标、生产可用性承诺或许可证授权结论。
- 保留工作区中所有既有未提交改动，Git 操作只允许明确指定 `README.md`。

---

### Task 1: 重写根目录项目文档

**Files:**
- Modify: `README.md`
- Reference: `pom.xml`
- Reference: `agents-*/pom.xml`
- Reference: `examples/simple-agent/pom.xml`
- Reference: `examples/simple-agent/src/main/resources/application.yml`
- Reference: `examples/simple-agent/src/main/java/com/ariza/agents/example/SimpleAgentApplication.java`
- Reference: `agents-core/src/main/java/com/ariza/agents/core/**/*.java`
- Reference: `agents-model-openai/src/main/java/com/ariza/agents/openai/OpenAIModelClient.java`
- Reference: `agents-session/src/main/java/com/ariza/agents/session/InMemorySessionStore.java`
- Reference: `agents-spring-boot-starter/src/main/java/com/ariza/agents/spring/*.java`
- Reference: `agents-tool-reflect/src/main/java/com/ariza/agents/tool/reflect/*.java`
- Reference: `agents-tracing/src/main/java/com/ariza/agents/tracing/*.java`

**Interfaces:**

- Consumes: 当前模块坐标 `com.ariza.agents:*:0.1.0-SNAPSHOT`、`Agent.builder()`、`new Runner(ModelClient)`、
  `Runner.runAgent(Agent, String)`、`RunResult.finalOutput()`、`agents.openai.api-key`、`agents.openai.default-model` 和
  HTTP `POST /agents/run`。
- Produces: 一份包含快速开始、API 示例、模块职责、核心流程、扩展点、构建测试、贡献与安全说明的根目录 README。

- [x] **Step 1: 写入完整 README 内容**

  使用一次有边界的补丁替换现有简版内容。章节顺序固定为：项目定位、核心特点、环境要求、项目结构、模块职责、快速开始、Java API 使用、Spring Boot 自动配置、核心概念、执行流程、扩展方式、构建测试、贡献指南、安全说明、版本与许可证。模块表必须覆盖根 POM 中的七个子模块，快速开始必须包含以下可执行命令和请求：

  ```bash
  export OPENAI_API_KEY="your-api-key"
  mvn clean install
  mvn -f examples/simple-agent/pom.xml spring-boot:run
  curl -X POST http://localhost:8080/agents/run \
    -H 'Content-Type: text/plain' \
    --data '介绍一下 Java Agent Runtime'
  ```

  HTTP 响应示例使用 `RunResult` 的真实字段：

  ```json
  {
    "finalOutput": "...",
    "runId": "...",
    "turns": 1
  }
  ```

- [x] **Step 2: 核对 Java 与 Spring Boot 示例**

  逐行对照当前签名，确认纯 Java 示例只调用以下现有 API：

  ```java
  ModelClient modelClient = new OpenAIModelClient(System.getenv("OPENAI_API_KEY"));
  Agent agent = Agent.builder()
          .name("Assistant")
          .instructions("You are a helpful assistant.")
          .model("gpt-4.1-mini")
          .build();
  RunResult result = new Runner(modelClient).runAgent(agent, "介绍一下 Java Agent Runtime");
  System.out.println(result.finalOutput());
  ```

  确认配置表只列出 `agents.openai.api-key` 和 `agents.openai.default-model`，默认模型写为 `gpt-4.1-mini`；确认 Spring Boot Starter 自动创建 `ModelClient` 和 `Runner`，同时允许用户提供同类型 Bean 覆盖默认实例。

- [x] **Step 3: 核对功能边界与扩展说明**

  搜索 README 中的 `Tool`、`ReflectionToolFactory`、`Handoff`、`Guardrail`、`SessionStore`、`TraceExporter` 和 `Streaming` 描述。保证工具、任务移交和护栏只描述公共契约；`ReflectionToolFactory.create(Object)` 明确会抛出 `UnsupportedOperationException`；`InMemorySessionStore` 和 `LogTraceExporter` 只作为可独立使用或扩展的实现介绍；不出现流式 API 使用示例。

- [x] **Step 4: 验证 Markdown 链接和仓库事实**

  运行：

  ```bash
  rg -o '\[[^]]+\]\(([^)]+)\)' README.md
  ```

  对每个相对路径链接执行本地存在性检查。随后核对：根 POM 版本为 `0.1.0-SNAPSHOT`，Java 版本为 `21`，Spring Boot 版本为 `3.5.8`，示例端口使用默认 `8080`，OpenAI 地址来自 `https://api.openai.com/v1/responses`。

- [x] **Step 5: 运行文档与项目验证**

  运行：

  ```bash
  git diff --check -- README.md
  mvn clean verify
  ```

  预期 `git diff --check` 无输出且退出码为 `0`；Maven Reactor Summary 中七个子模块和父项目均为 `SUCCESS`。如果 Maven 因网络或外部服务限制失败，保留完整失败原因并确认失败并非 README 变更造成。

- [x] **Step 6: 审查最终差异与工作区隔离**

  运行：

  ```bash
  git diff -- README.md
  git status --short
  ```

  确认 README 没有无依据的能力、发布或许可证声明；确认本任务没有改动任何用户已有源码文件。只有获得用户明确授权后，才单独暂存并提交 `README.md`，提交信息使用 `docs: 完善项目 README`。

## Self-Review

- 规格覆盖：双层阅读路径、七个模块、快速开始、Java API、Spring Boot 配置、核心契约、执行流程、扩展点、构建测试、贡献、安全、版本与许可证说明均落实到 Task 1。
- 内容完整性：每个步骤均包含明确的目标、命令、示例或验收条件。
- 类型一致性：计划使用的构建器、构造器、方法、记录访问器、配置键、HTTP 端点和响应字段均与当前源码一致。
- 范围隔离：实施文件仅为 `README.md`，所有验证均为只读检查或项目构建，不修改用户已有源码变更。
