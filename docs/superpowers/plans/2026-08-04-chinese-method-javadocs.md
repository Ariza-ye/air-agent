# Java 方法中文注释实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为仓库内所有显式声明的 Java 方法、构造器、记录紧凑构造器、接口方法和注解成员补充准确的中文 Javadoc 注释。

**Architecture:** 本次仅修改源码注释，不调整签名、控制流、依赖或运行行为。按 Maven 模块分批处理，并通过源码覆盖检查、差异检查和 Maven 全量验证确保没有遗漏或行为变化。

**Tech Stack:** Java 21、Maven、JUnit 5、Javadoc。

## Global Constraints

- 注释必须使用中文，说明方法职责，并为有参数或返回值的方法补充 `@param`、`@return`；确有可观察异常约束时补充 `@throws`。
- 覆盖生产代码与测试代码中的所有显式方法和构造器，包括接口方法、注解成员、记录紧凑构造器、Spring Bean 方法和示例入口。
- 编译器为记录自动生成的访问器、构造器及 `equals`、`hashCode`、`toString` 不属于显式源码声明，不添加无法附着到源码声明上的方法注释。
- Lambda 表达式和方法调用不是方法声明，不作为 Javadoc 覆盖目标。
- 不修改现有业务逻辑、API 签名、格式之外的代码，也不改动用户已有的未跟踪 `AGENTS.md`。

---

### Task 1: 核心 Agent 与运行器 API

**Files:**

- Modify: `agents-core/src/main/java/com/ariza/agents/core/Agent.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/AgentRunException.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/Handoff.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/RunContext.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/RunResult.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/Runner.java`

**Interfaces:**
- Consumes: 现有构造器、构建器与运行入口签名。
- Produces: 每个显式方法或构造器前的中文 Javadoc，不改变任何 Java 接口。

- [x] **Step 1: 为记录紧凑构造器和普通构造器补充职责、参数及异常说明**
- [x] **Step 2: 为 `Agent.Builder`、`Runner` 和 `RunContext` 的全部方法补充中文 Javadoc**
- [x] **Step 3: 运行 `mvn -pl agents-core -am test`，预期全部测试通过**

### Task 2: 核心模型、工具、护栏与会话契约

**Files:**

- Modify: `agents-core/src/main/java/com/ariza/agents/core/guardrail/Guardrail.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/guardrail/GuardrailResult.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/model/ModelClient.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/model/ModelRequest.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/model/ModelResponse.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/session/MessageItem.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/session/SessionStore.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/tool/Tool.java`
- Modify: `agents-core/src/main/java/com/ariza/agents/core/tool/ToolResult.java`

**Interfaces:**
- Consumes: 现有模型调用、护栏、工具和会话接口。
- Produces: 接口方法、工厂方法及显式构造器的完整中文 Javadoc。

- [x] **Step 1: 为全部接口方法逐一写明输入、输出和职责**
- [x] **Step 2: 为记录紧凑构造器、便利构造器和静态工厂方法补充中文 Javadoc**
- [x] **Step 3: 运行 `mvn -pl agents-core -am test`，预期全部测试通过**

### Task 3: 扩展模块实现

**Files:**

- Modify: `agents-model-openai/src/main/java/com/ariza/agents/openai/OpenAIModelClient.java`
- Modify: `agents-session/src/main/java/com/ariza/agents/session/InMemorySessionStore.java`
- Modify: `agents-spring-boot-starter/src/main/java/com/ariza/agents/spring/AgentsAutoConfiguration.java`
- Modify: `agents-spring-boot-starter/src/main/java/com/ariza/agents/spring/AgentsProperties.java`
- Modify: `agents-tool-reflect/src/main/java/com/ariza/agents/tool/reflect/AgentTool.java`
- Modify: `agents-tool-reflect/src/main/java/com/ariza/agents/tool/reflect/ReflectionToolFactory.java`
- Modify: `agents-tool-reflect/src/main/java/com/ariza/agents/tool/reflect/ToolParam.java`
- Modify: `agents-tracing/src/main/java/com/ariza/agents/tracing/LogTraceExporter.java`
- Modify: `agents-tracing/src/main/java/com/ariza/agents/tracing/TraceExporter.java`

**Interfaces:**
- Consumes: 各扩展模块的现有实现与 SPI。
- Produces: 构造器、重写方法、Bean 工厂、配置访问器、注解成员和追踪导出方法的中文 Javadoc。

- [x] **Step 1: 为 OpenAI 客户端构造器和模型调用实现补充参数、返回值及异常说明**
- [x] **Step 2: 为会话、Spring、反射工具和追踪模块的全部显式方法补充中文 Javadoc**
- [x] **Step 3: 分模块执行 Maven 测试，预期所有模块编译与测试通过**

### Task 4: 示例与测试

**Files:**

- Modify: `examples/simple-agent/src/main/java/com/ariza/agents/example/SimpleAgentApplication.java`
- Modify: `agents-core/src/test/java/com/ariza/agents/core/RunnerTest.java`

**Interfaces:**
- Consumes: 示例应用构造器、HTTP 入口、主入口和现有测试方法。
- Produces: 面向使用者的中文 Javadoc 与清晰的中文测试意图说明。

- [x] **Step 1: 为示例应用构造器、HTTP 端点和 `main` 方法补充中文 Javadoc**
- [x] **Step 2: 为测试方法补充说明可观察行为的中文 Javadoc**
- [x] **Step 3: 运行示例模块及其依赖测试，预期编译与测试通过**

### Task 5: 覆盖率与全量验证

**Files:**
- Verify: 所有 `src/main/java` 与 `src/test/java` 下的 `.java` 文件。

**Interfaces:**
- Consumes: 前四项产生的注释变更。
- Produces: 无遗漏、无逻辑改动且可通过全量构建的最终结果。

- [x] **Step 1: 使用本地源码扫描复核每个显式方法或构造器前均存在中文 Javadoc**
- [x] **Step 2: 运行 `git diff --check`，预期无空白错误**
- [x] **Step 3: 运行 `git diff --word-diff=porcelain`，确认变更仅包含注释与必要换行**
- [x] **Step 4: 运行 `mvn clean verify`，预期所有模块编译和测试通过**

## Self-Review

- 规格覆盖：生产代码、测试代码、接口、注解、构造器、记录紧凑构造器及示例入口均有对应任务。
- 占位符检查：计划不包含待定实现或未定义接口。
- 类型一致性：本次不引入或修改类型、方法签名及属性名称。
