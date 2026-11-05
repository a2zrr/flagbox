-- Append-only history of changes. No foreign keys on purpose: audit rows
-- should survive even if the entity they describe is removed later.
CREATE TABLE audit_logs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id     BIGINT       NOT NULL,
    environment_id BIGINT,
    entity_type    VARCHAR(32)  NOT NULL CHECK (entity_type IN ('PROJECT', 'ENVIRONMENT', 'FLAG', 'FLAG_CONFIG', 'API_KEY')),
    entity_key     VARCHAR(128) NOT NULL,
    action         VARCHAR(16)  NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    actor          VARCHAR(120) NOT NULL,
    old_value      JSONB,
    new_value      JSONB,
    occurred_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_logs_entity_history ON audit_logs (project_id, entity_type, entity_key, occurred_at DESC);
