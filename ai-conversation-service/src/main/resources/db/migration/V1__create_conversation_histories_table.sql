-- Owned by ai-conversation-service. One row per completed conversation
-- (created when the WS for that conversationId closes). conversationId is
-- the natural key; id is a ULID for housekeeping. Messages are stored as a
-- jsonb array of {role, content} objects matching the live in-memory shape.
CREATE TABLE IF NOT EXISTS conversation_histories (
    id               VARCHAR(26)  PRIMARY KEY,
    conversation_id  VARCHAR(26)  NOT NULL UNIQUE,
    business_id      VARCHAR(26)  NOT NULL,
    language         VARCHAR(16),
    message_count    INTEGER      NOT NULL DEFAULT 0,
    messages         JSONB        NOT NULL,
    input_tokens     BIGINT,
    output_tokens    BIGINT,
    started_at       TIMESTAMPTZ  NOT NULL,
    ended_at         TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conv_histories_business_ended
    ON conversation_histories (business_id, ended_at DESC);
