package com.ariza.agent.session.pg;

import com.ariza.agent.core.session.MessageItem;
import com.ariza.agent.core.session.MessageRole;
import com.ariza.agent.core.session.SessionStore;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/**
 * 使用 PostgreSQL 持久化会话消息。
 *
 * @author ariza
 * @since 2026-08-10 16:16:02
 */
public class PgSessionStore implements SessionStore {

    private static final String TABLE_NAME = "agent_session_messages";

    private static final String LOAD_SQL = """
            SELECT role, content, created_at
            FROM agent_session_messages
            WHERE session_id = ?
            ORDER BY id
            """;

    private static final String APPEND_SQL = """
            INSERT INTO agent_session_messages (session_id, role, content, created_at)
            VALUES (?, ?, ?, ?)
            """;

    private static final String EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM agent_session_messages
                WHERE session_id = ?
            )
            """;

    private static final String CLEAR_SQL = """
            DELETE FROM agent_session_messages
            WHERE session_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 PostgreSQL 会话存储。
     *
     * @param dataSource MyBatis 与会话存储共用的数据源
     */
    public PgSessionStore(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    /**
     * Bean 初始化后检查当前数据库 Schema 是否已创建会话消息表。
     */
    public void post() {
        Boolean tableExists = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            try (ResultSet tables = connection.getMetaData()
                    .getTables(catalog, schema, null, new String[]{"TABLE", "PARTITIONED TABLE"})) {
                return containsSessionMessagesTable(tables);
            }
        });
        if (!Boolean.TRUE.equals(tableExists)) {
            throw new IllegalStateException(
                    "启用 agents.session.type=pg 需要预先创建数据表 " + TABLE_NAME);
        }
    }

    /**
     * 按写入顺序加载指定会话的全部消息。
     *
     * @param sessionId 会话唯一标识
     * @return 当前会话的消息快照
     */
    @Override
    @Transactional(readOnly = true)
    public List<MessageItem> load(String sessionId) {
        String checkedSessionId = requireSessionId(sessionId);
        return jdbcTemplate.query(LOAD_SQL, (resultSet, rowNumber) -> new MessageItem(
                MessageRole.valueOf(resultSet.getString("role")),
                resultSet.getString("content"),
                resultSet.getTimestamp("created_at").toInstant()), checkedSessionId);
    }

    /**
     * 查询指定会话是否已保存消息。
     *
     * @param sessionId 会话唯一标识
     * @return 会话至少存在一条消息时返回 {@code true}
     */
    @Override
    @Transactional(readOnly = true)
    public Boolean exists(String sessionId) {
        String checkedSessionId = requireSessionId(sessionId);
        Boolean exists = jdbcTemplate.queryForObject(EXISTS_SQL, Boolean.class, checkedSessionId);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 在一个事务中向指定会话追加消息。
     *
     * @param sessionId 会话唯一标识
     * @param items     需要追加的消息列表
     */
    @Override
    @Transactional
    public void append(String sessionId, List<MessageItem> items) {
        String checkedSessionId = requireSessionId(sessionId);
        List<MessageItem> checkedItems = List.copyOf(Objects.requireNonNull(items, "items"));
        checkedItems.forEach(this::requireItem);
        if (checkedItems.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(APPEND_SQL, checkedItems, checkedItems.size(), (statement, item) -> {
            statement.setString(1, checkedSessionId);
            statement.setString(2, item.role().name());
            statement.setString(3, item.content());
            statement.setTimestamp(4, Timestamp.from(item.createdAt()));
        });
    }

    /**
     * 删除指定会话的全部消息。
     *
     * @param sessionId 会话唯一标识
     */
    @Override
    @Transactional
    public void clear(String sessionId) {
        String checkedSessionId = requireSessionId(sessionId);
        jdbcTemplate.update(CLEAR_SQL, checkedSessionId);
    }

    /**
     * 校验会话标识，避免无效数据进入数据库。
     *
     * @param sessionId 待校验的会话标识
     * @return 去除首尾空白后的会话标识
     */
    private String requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessionId.trim();
    }

    /**
     * 校验待写入的会话消息。
     *
     * @param item 待校验消息
     */
    private void requireItem(MessageItem item) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(item.role(), "item.role");
        Objects.requireNonNull(item.content(), "item.content");
        Objects.requireNonNull(item.createdAt(), "item.createdAt");
    }

    /**
     * 在 JDBC 元数据结果中查找会话消息表。
     *
     * @param tables 当前 Schema 的数据表元数据
     * @return 是否存在会话消息表
     * @throws SQLException 读取 JDBC 元数据失败
     */
    private boolean containsSessionMessagesTable(ResultSet tables) throws SQLException {
        while (tables.next()) {
            if (TABLE_NAME.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                return true;
            }
        }
        return false;
    }
}
