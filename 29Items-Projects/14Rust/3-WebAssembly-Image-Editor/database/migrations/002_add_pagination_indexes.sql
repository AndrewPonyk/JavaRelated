BEGIN;

DROP INDEX IF EXISTS editor_projects_owner_updated_idx;
CREATE INDEX editor_projects_owner_updated_idx
    ON editor_projects (owner_id, updated_at DESC, id DESC);

DROP INDEX IF EXISTS edit_presets_owner_updated_idx;
CREATE INDEX edit_presets_owner_updated_idx
    ON edit_presets (owner_id, updated_at DESC, id DESC);

COMMIT;
