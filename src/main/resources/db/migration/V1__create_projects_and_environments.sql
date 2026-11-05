-- Projects group flags. Keys are URL-safe slugs used in API paths.
CREATE TABLE projects (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key         VARCHAR(64)  NOT NULL UNIQUE CHECK (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    created_at  TIMESTAMPTZ  NOT NULL
);

-- Environments belong to a project (e.g. development, production).
CREATE TABLE environments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    key         VARCHAR(64)  NOT NULL CHECK (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_environments_project_key UNIQUE (project_id, key)
);
