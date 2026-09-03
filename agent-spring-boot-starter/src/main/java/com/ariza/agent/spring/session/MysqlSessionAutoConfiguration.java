package com.ariza.agent.spring.session;

import com.ariza.agent.core.session.SessionStore;
import com.ariza.agent.session.mysql.MysqlSessionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * MySQL 会话存储自动配置。
 *
 * @author ariza
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "agents.session", name = "type", havingValue = "mysql")
@EnableConfigurationProperties(SessionProperties.class)
public class MysqlSessionAutoConfiguration {

    /**
     * 使用 MyBatis 所在应用的数据源创建 MySQL 会话存储。
     *
     * @param dataSource MyBatis 与会话存储共用的数据源
     * @return MySQL 会话存储
     */
    @Bean(initMethod = "post")
    @ConditionalOnMissingBean(SessionStore.class)
    MysqlSessionStore mysqlSessionStore(DataSource dataSource) {
        return new MysqlSessionStore(dataSource);
    }
}
