-- API keys are scoped to one project and one environment.
-- Only a SHA-256 hash of the raw key is stored; key_prefix is kept for display.
CREATE TABLE api_keys (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id     BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    environment_id BIGINT       NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    type           VARCHAR(20)  NOT NULL CHECK (type IN ('ADMIN', 'CLIENT')),
    key_prefix     VARCHAR(32)  NOT NULL,
    key_hash       VARCHAR(64)  NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_project_environment ON api_keys (project_id, environment_id);
