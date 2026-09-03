package com.ariza.agent.core;

import com.ariza.agent.core.model.ModelClient;
import com.ariza.agent.core.session.SessionStore;
import com.ariza.agent.core.tracing.TraceExporter;

import java.util.Objects;

/**
 * Agent 运行入口，负责参数校验并将执行委托给内部运行引擎。
 *
 * @author ariza
 */
public final class Runner {
    private static final int DEFAULT_MAX_TURNS = 10;

    private static volatile ModelClient defaultModelClient;
    private final AgentRunEngine runEngine;

    /**
     * 使用默认最大轮次创建运行器。
     *
     * @param modelClient  执行模型请求的客户端
     * @param sessionStore 消息存储
     * @throws NullPointerException 当模型客户端为 {@code null} 时抛出
     */
    public Runner(ModelClient modelClient, SessionStore sessionStore) {
        this(modelClient, DEFAULT_MAX_TURNS, sessionStore, null);
    }

    /**
     * 使用默认最大轮次创建无会话运行器。
     *
     * @param modelClient 执行模型请求的客户端
     * @throws NullPointerException 当模型客户端为 {@code null} 时抛出
     */
    public Runner(ModelClient modelClient) {
        this(modelClient, DEFAULT_MAX_TURNS, null, null);
    }

    /**
     * 使用可选会话存储和追踪导出器创建运行器。
     *
     * @param modelClient   执行模型请求的客户端
     * @param sessionStore  消息存储；不需要会话时可为 {@code null}
     * @param traceExporter 追踪导出器；不需要追踪时可为 {@code null}
     * @throws NullPointerException 当模型客户端为 {@code null} 时抛出
     */
    public Runner(ModelClient modelClient, SessionStore sessionStore, TraceExporter traceExporter) {
        this(modelClient, DEFAULT_MAX_TURNS, sessionStore, traceExporter);
    }

    /**
     * 使用指定模型客户端和最大轮次创建运行器。
     *
     * @param modelClient  执行模型请求的客户端
     * @param maxTurns     单次运行允许的最大轮次
     * @param sessionStore 消息存储；不需要会话时可为 {@code null}
     * @throws NullPointerException     当模型客户端为 {@code null} 时抛出
     * @throws IllegalArgumentException 当最大轮次小于 1 时抛出
     */
    public Runner(ModelClient modelClient, int maxTurns, SessionStore sessionStore) {
        this(modelClient, maxTurns, sessionStore, null);
    }

    /**
     * 使用指定最大轮次、可选会话存储和可选追踪导出器创建运行器。
     *
     * @param modelClient   执行模型请求的客户端
     * @param maxTurns      单次运行允许的最大轮次
     * @param sessionStore  消息存储；不需要会话时可为 {@code null}
     * @param traceExporter 追踪导出器；不需要追踪时可为 {@code null}
     * @throws NullPointerException     当模型客户端为 {@code null} 时抛出
     * @throws IllegalArgumentException 当最大轮次小于 1 时抛出
     */
    public Runner(ModelClient modelClient, int maxTurns, SessionStore sessionStore,
                  TraceExporter traceExporter) {
        Objects.requireNonNull(modelClient, "modelClient");
        if (maxTurns < 1) {
            throw new IllegalArgumentException("maxTurns must be positive");
        }
        this.runEngine = new AgentRunEngine(modelClient, maxTurns, sessionStore, traceExporter);
    }

    /**
     * 设置供静态运行入口使用的默认模型客户端。
     *
     * @param client 默认模型客户端
     * @throws NullPointerException 当模型客户端为 {@code null} 时抛出
     */
    public static void setDefaultModelClient(ModelClient client) {
        defaultModelClient = Objects.requireNonNull(client, "client");
    }

    /**
     * 使用已配置的默认模型客户端执行一次 Agent 请求。
     *
     * @param agent 要执行的智能体
     * @param input 用户输入文本
     * @return 智能体运行结果
     * @throws AgentRunException 当尚未配置默认模型客户端或智能体运行失败时抛出
     */
    public static RunResult run(Agent agent, String input) {
        ModelClient client = defaultModelClient;
        if (client == null) {
            throw new AgentRunException("未配置默认 ModelClient");
        }
        return new Runner(client).runAgent(agent, input, null);
    }

    /**
     * 使用当前运行器执行一次 Agent 请求。
     *
     * @param agent     要执行的智能体
     * @param input     用户输入文本
     * @param sessionId 会话标识
     * @param context   运行上下文；传入 {@code null} 时自动创建
     * @return 包含最终输出、运行标识和轮次的运行结果
     * @throws NullPointerException 当智能体、输入为 {@code null}，或启用会话存储但会话标识为 {@code null} 时抛出
     */
    public RunResult runAgent(Agent agent, String input, String sessionId, RunContext context) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(input, "input");
        RunContext actualContext = context == null ? new RunContext() : context;
        return runEngine.run(agent, input, sessionId, actualContext);
    }

    /**
     * 使用新运行上下文执行一次 Agent 请求。
     *
     * @param agent     要执行的智能体
     * @param input     用户输入文本
     * @param sessionId 会话标识
     * @return 包含最终输出、运行标识和轮次的运行结果
     */
    public RunResult runAgent(Agent agent, String input, String sessionId) {
        return runAgent(agent, input, sessionId, new RunContext());
    }

    /**
     * 获取单次运行允许的最大轮次。
     *
     * @return 最大轮次数
     */
    public int maxTurns() {
        return runEngine.maxTurns();
    }
}
