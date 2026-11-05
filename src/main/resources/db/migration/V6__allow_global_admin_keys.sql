-- A key is either global (no project, no environment, ADMIN only) or scoped to
-- exactly one project and one environment. The bootstrap key is global.
ALTER TABLE api_keys ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE api_keys ALTER COLUMN environment_id DROP NOT NULL;
ALTER TABLE api_keys ADD CONSTRAINT ck_api_keys_scope CHECK (
    (project_id IS NULL AND environment_id IS NULL AND type = 'ADMIN')
    OR (project_id IS NOT NULL AND environment_id IS NOT NULL));

-- Changes to global keys have no project, so the audit row cannot require one.
ALTER TABLE audit_logs ALTER COLUMN project_id DROP NOT NULL;
