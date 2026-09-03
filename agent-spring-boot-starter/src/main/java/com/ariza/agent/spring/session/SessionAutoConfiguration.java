package com.ariza.agent.spring.session;

import com.ariza.agent.core.session.SessionStore;
import com.ariza.agent.session.InMemorySessionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 默认会话存储自动配置。
 *
 * @author ariza
 */
@AutoConfiguration(after = {PgSessionAutoConfiguration.class, MysqlSessionAutoConfiguration.class})
@EnableConfigurationProperties(SessionProperties.class)
public class SessionAutoConfiguration {

    /**
     * 在未配置其他实现时创建进程内会话存储。
     *
     * @return 进程内会话存储
     */
    @Bean
    @ConditionalOnMissingBean(SessionStore.class)
    @ConditionalOnProperty(prefix = "agents.session", name = "type", havingValue = "memory", matchIfMissing = true)
    InMemorySessionStore inMemorySessionStore() {
        return new InMemorySessionStore();
    }

    /**
     * 在启用 PG 但缺少数据源时给出明确的启动错误。
     *
     * @return 不会正常创建的依赖检查对象
     */
    @Bean
    @ConditionalOnProperty(prefix = "agents.session", name = "type", havingValue = "pg")
    @ConditionalOnMissingBean(SessionStore.class)
    Object missingPgSessionDependencies() {
        throw new IllegalStateException(
                "启用 agents.session.type=pg 需要 DataSource、spring-jdbc 和 PostgreSQL JDBC 驱动");
    }

    /**
     * 在启用 MySQL 但缺少数据源时给出明确的启动错误。
     *
     * @return 不会正常创建的依赖检查对象
     */
    @Bean
    @ConditionalOnProperty(prefix = "agents.session", name = "type", havingValue = "mysql")
    @ConditionalOnMissingBean(SessionStore.class)
    Object missingMysqlSessionDependencies() {
        throw new IllegalStateException(
                "启用 agents.session.type=mysql 需要 DataSource、spring-jdbc 和 MySQL JDBC 驱动");
    }
}
