CREATE TABLE IF NOT EXISTS businesses (
    id              VARCHAR(26)  PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    category        VARCHAR(100),
    description     TEXT,
    location        VARCHAR(255),
    operating_hours VARCHAR(255),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
