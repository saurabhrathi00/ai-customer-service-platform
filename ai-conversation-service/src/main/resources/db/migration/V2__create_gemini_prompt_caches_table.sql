CREATE TABLE IF NOT EXISTS gemini_prompt_caches (
    id                VARCHAR(26)  PRIMARY KEY,
    business_id       VARCHAR(26)  NOT NULL UNIQUE,
    knowledge_hash    VARCHAR(64)  NOT NULL,
    gemini_cache_name VARCHAR(128) NOT NULL,
    model             VARCHAR(64)  NOT NULL,
    expire_time       TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
