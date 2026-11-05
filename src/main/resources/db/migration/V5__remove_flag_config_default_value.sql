-- The server never serves a default value: when a flag is not found, the calling
-- application supplies its own fallback. The column is dropped, not edited in V2.
ALTER TABLE flag_configs DROP COLUMN default_value;
