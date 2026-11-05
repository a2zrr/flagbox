-- A flag belongs to one project. Its key is unique within that project.
CREATE TABLE flags (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    key         VARCHAR(64)  NOT NULL CHECK (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('BOOLEAN')),
    archived    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_flags_project_key UNIQUE (project_id, key)
);

-- One configuration per flag per environment. rules is an ordered JSON array;
-- the first matching rule wins. See docs/ARCHITECTURE.md for the format.
CREATE TABLE flag_configs (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flag_id            BIGINT      NOT NULL REFERENCES flags (id) ON DELETE CASCADE,
    environment_id     BIGINT      NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    enabled            BOOLEAN     NOT NULL DEFAULT FALSE,
    rollout_percentage INTEGER     NOT NULL DEFAULT 100 CHECK (rollout_percentage BETWEEN 0 AND 100),
    default_value      BOOLEAN     NOT NULL DEFAULT FALSE,
    rules              JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_flag_configs_flag_environment UNIQUE (flag_id, environment_id)
);
