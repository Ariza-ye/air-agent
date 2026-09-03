CREATE TABLE IF NOT EXISTS agent_session_messages
(
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    role       VARCHAR(32)  NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_agent_session_messages_role
        CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    INDEX idx_agent_session_messages_session_id_id (session_id, id)
);
