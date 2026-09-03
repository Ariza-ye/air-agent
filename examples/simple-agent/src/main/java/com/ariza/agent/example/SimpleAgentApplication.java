package com.ariza.agent.example;

import com.ariza.agent.core.*;
import com.ariza.agent.core.session.SessionStore;
import com.ariza.agent.spring.AgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * @author ariza
 */
@SpringBootApplication
@RestController
public class SimpleAgentApplication {
    private static final Logger log = LoggerFactory.getLogger(SimpleAgentApplication.class);
    private final Runner runner;
    private final Agent agent;
    private final SessionStore sessionStore;

    /**
     * 使用注入的运行器和模型名称创建示例应用。
     *
     * @param runner 执行智能体请求的运行器
     * @param tools  Spring 容器中扫描得到的工具列表
     * @param model  示例智能体使用的模型名称
     */
    public SimpleAgentApplication(Runner runner,
                                  AgentTools tools,
                                  SessionStore sessionStore,
                                  @Value("${agents.ai.default-model:gpt-4.1-mini}") String model) {
        this.runner = runner;
        this.sessionStore = sessionStore;
        this.agent = Agent.builder()
                .name("小爱小爱")
                .instructions("你是小爱,你是一个系统信息查询助手")
                .model(model)
                .tools(tools.getTools())
                .build();
    }

    /**
     * 启动 Spring Boot 示例应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        loadDotEnv(Path.of(".env"));
        SpringApplication.run(SimpleAgentApplication.class, args);
    }


    /**
     * 加载本地环境变量
     *
     * @param path
     */
    private static void loadDotEnv(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .filter(line -> line.contains("="))
                    .forEach(line -> {
                        int separator = line.indexOf('=');
                        String key = line.substring(0, separator).trim();
                        String value = line.substring(separator + 1).trim();
                        if (key.isEmpty() || System.getProperty(key) != null) {
                            return;
                        }
                        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        System.setProperty(key, value);
                    });
        } catch (IOException e) {
            log.warn("加载 .env 文件失败: {}", path, e);
        }
    }

    /**
     * 接收文本输入并执行示例智能体。
     *
     * @param req HTTP 请求体中的用户输入
     * @return 智能体运行结果
     */
    @PostMapping("/agents/run")
    RunResult run(@RequestBody AgentRunReq req) {
        String sessionId;
        if (StringUtils.hasText(req.sessionId())) {
            sessionId = req.sessionId();
        } else {
            sessionId = UUID.randomUUID().toString();
        }
        // 获取用户信息
        RunContext runContext = new RunContext();
        runContext.attributes().put(RunContextAttrEnum.USER_ID.name(), "0000000");
        RunResult runResult = runner.runAgent(agent, req.input(), sessionId, runContext);
        // 打印历史消息
        log.info("Run agent {} finished", sessionStore.load(sessionId));
        return runResult;
    }
}
