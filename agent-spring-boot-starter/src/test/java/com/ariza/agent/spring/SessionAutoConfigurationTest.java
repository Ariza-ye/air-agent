package com.ariza.agent.spring;

import com.ariza.agent.core.session.MessageItem;
import com.ariza.agent.core.session.MessageRole;
import com.ariza.agent.core.session.SessionStore;
import com.ariza.agent.session.InMemorySessionStore;
import com.ariza.agent.session.mysql.MysqlSessionStore;
import com.ariza.agent.session.pg.PgSessionStore;
import com.ariza.agent.spring.session.MysqlSessionAutoConfiguration;
import com.ariza.agent.spring.session.PgSessionAutoConfiguration;
import com.ariza.agent.spring.session.SessionAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author ariza
 */
class SessionAutoConfigurationTest {

    private final ApplicationContextRunner pgContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class,
                    TransactionAutoConfiguration.class,
                    PgSessionAutoConfiguration.class,
                    SessionAutoConfiguration.class))
            .withPropertyValues("agents.session.type=pg")
            .withBean(DataSource.class, this::initializedDataSource);

    private final ApplicationContextRunner mysqlContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class,
                    TransactionAutoConfiguration.class,
                    MysqlSessionAutoConfiguration.class,
                    SessionAutoConfiguration.class))
            .withPropertyValues("agents.session.type=mysql")
            .withBean(DataSource.class, this::initializedMysqlDataSource);

    /**
     * 验证启用 PG 并执行建表脚本后，可完整追加、加载和清除消息。
     */
    @Test
    void storesMessagesWhenPgIsEnabled() {
        pgContextRunner.run(context -> {
            assertThat(context).hasSingleBean(SessionStore.class);
            assertThat(context).hasSingleBean(PgSessionStore.class);

            SessionStore sessionStore = context.getBean(SessionStore.class);
            List<MessageItem> messages = List.of(
                    new MessageItem(MessageRole.USER, "你好", Instant.parse("2026-08-10T08:00:00Z")),
                    new MessageItem(MessageRole.ASSISTANT, "你好，有什么可以帮你？",
                            Instant.parse("2026-08-10T08:00:01Z")));

            assertThat(sessionStore.exists("session-1")).isFalse();
            sessionStore.append("session-1", messages);

            assertThat(sessionStore.exists("session-1")).isTrue();
            assertThat(sessionStore.exists(" session-1 ")).isTrue();
            assertThat(sessionStore.load("session-1")).containsExactlyElementsOf(messages);
            assertThat(sessionStore.load("missing-session")).isEmpty();

            sessionStore.clear("session-1");
            assertThat(sessionStore.exists("session-1")).isFalse();
            assertThat(sessionStore.load("session-1")).isEmpty();
        });
    }

    /**
     * 验证启用 MySQL 并执行建表脚本后，可完整追加、加载和清除消息。
     */
    @Test
    void storesMessagesWhenMysqlIsEnabled() {
        mysqlContextRunner.run(context -> {
            assertThat(context).hasSingleBean(SessionStore.class);
            assertThat(context).hasSingleBean(MysqlSessionStore.class);

            SessionStore sessionStore = context.getBean(SessionStore.class);
            List<MessageItem> messages = List.of(
                    new MessageItem(MessageRole.USER, "你好", Instant.parse("2026-08-10T08:00:00Z")),
                    new MessageItem(MessageRole.ASSISTANT, "你好，有什么可以帮你？",
                            Instant.parse("2026-08-10T08:00:01Z")));

            assertThat(sessionStore.exists("session-1")).isFalse();
            sessionStore.append("session-1", messages);

            assertThat(sessionStore.exists("session-1")).isTrue();
            assertThat(sessionStore.exists(" session-1 ")).isTrue();
            assertThat(sessionStore.load("session-1")).containsExactlyElementsOf(messages);
            assertThat(sessionStore.load("missing-session")).isEmpty();

            sessionStore.clear("session-1");
            assertThat(sessionStore.exists("session-1")).isFalse();
            assertThat(sessionStore.load("session-1")).isEmpty();
        });
    }

    /**
     * 验证未指定存储类型时仍使用内存实现。
     */
    @Test
    void usesMemoryStoreByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionStore.class);
                    assertThat(context).hasSingleBean(InMemorySessionStore.class);
                });
    }

    /**
     * 验证启用 PG 但缺少数据源时启动失败信息清晰。
     */
    @Test
    void failsClearlyWhenDataSourceIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class))
                .withPropertyValues("agents.session.type=pg")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "启用 agents.session.type=pg 需要 DataSource、spring-jdbc 和 PostgreSQL JDBC 驱动"));
    }

    /**
     * 验证启用 PG 但未创建消息表时应用启动失败。
     */
    @Test
    void failsWhenPgTableIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PgSessionAutoConfiguration.class,
                        SessionAutoConfiguration.class))
                .withPropertyValues("agents.session.type=pg")
                .withBean(DataSource.class, () -> h2DataSource("agent-session-missing"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "启用 agents.session.type=pg 需要预先创建数据表 agent_session_messages"));
    }

    /**
     * 验证启用 MySQL 但缺少数据源时启动失败信息清晰。
     */
    @Test
    void failsClearlyWhenMysqlDataSourceIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class))
                .withPropertyValues("agents.session.type=mysql")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "启用 agents.session.type=mysql 需要 DataSource、spring-jdbc 和 MySQL JDBC 驱动"));
    }

    /**
     * 验证启用 MySQL 但未创建消息表时应用启动失败。
     */
    @Test
    void failsWhenMysqlTableIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MysqlSessionAutoConfiguration.class,
                        SessionAutoConfiguration.class))
                .withPropertyValues("agents.session.type=mysql")
                .withBean(DataSource.class, () -> h2MysqlDataSource("agent-session-mysql-missing"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "启用 agents.session.type=mysql 需要预先创建数据表 agent_session_messages"));
    }

    /**
     * 创建并初始化测试用数据源。
     *
     * @return 已创建会话消息表的数据源
     */
    private DataSource initializedDataSource() {
        DataSource dataSource = h2DataSource("agent-session-initialized");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/postgresql/agent_session_messages.sql"));
        } catch (Exception exception) {
            throw new IllegalStateException("初始化测试数据库失败", exception);
        }
        return dataSource;
    }

    /**
     * 创建并初始化 MySQL 兼容模式的测试数据源。
     *
     * @return 已创建会话消息表的数据源
     */
    private DataSource initializedMysqlDataSource() {
        DataSource dataSource = h2MysqlDataSource("agent-session-mysql-initialized");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/mysql/agent_session_messages.sql"));
        } catch (Exception exception) {
            throw new IllegalStateException("初始化 MySQL 测试数据库失败", exception);
        }
        return dataSource;
    }

    /**
     * 创建 PostgreSQL 兼容模式的 H2 测试数据源。
     *
     * @param databaseName 数据库名称
     * @return 测试数据源
     */
    private DataSource h2DataSource(String databaseName) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private DataSource h2MysqlDataSource(String databaseName) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
}
