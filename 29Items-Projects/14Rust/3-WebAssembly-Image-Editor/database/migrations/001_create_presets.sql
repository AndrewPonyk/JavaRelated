BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE editor_users (
    id UUID PRIMARY KEY,
    display_name VARCHAR(60) NOT NULL CHECK (length(trim(display_name)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE editor_projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES editor_users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT editor_projects_id_owner_unique UNIQUE (id, owner_id),
    CONSTRAINT editor_projects_owner_name_unique UNIQUE (owner_id, name)
);

CREATE TABLE edit_presets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES editor_users(id) ON DELETE CASCADE,
    project_id UUID NULL,
    name VARCHAR(80) NOT NULL CHECK (length(trim(name)) > 0),
    recipe JSONB NOT NULL,
    schema_version SMALLINT NOT NULL CHECK (schema_version = 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT edit_presets_recipe_object CHECK (jsonb_typeof(recipe) = 'object'),
    CONSTRAINT edit_presets_owner_name_unique UNIQUE (owner_id, name),
    CONSTRAINT edit_presets_project_owner_fk
        FOREIGN KEY (project_id, owner_id)
        REFERENCES editor_projects (id, owner_id)
        ON DELETE SET NULL (project_id)
);

CREATE INDEX editor_projects_owner_updated_idx
    ON editor_projects (owner_id, updated_at DESC);

CREATE INDEX edit_presets_owner_updated_idx
    ON edit_presets (owner_id, updated_at DESC);

CREATE INDEX edit_presets_project_idx
    ON edit_presets (project_id)
    WHERE project_id IS NOT NULL;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER editor_projects_set_updated_at
BEFORE UPDATE ON editor_projects
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER edit_presets_set_updated_at
BEFORE UPDATE ON edit_presets
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE editor_projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE editor_projects FORCE ROW LEVEL SECURITY;
ALTER TABLE edit_presets ENABLE ROW LEVEL SECURITY;
ALTER TABLE edit_presets FORCE ROW LEVEL SECURITY;

CREATE POLICY editor_projects_owner_policy ON editor_projects
    USING (owner_id = current_setting('app.user_id', true)::uuid)
    WITH CHECK (owner_id = current_setting('app.user_id', true)::uuid);

CREATE POLICY edit_presets_owner_policy ON edit_presets
    USING (owner_id = current_setting('app.user_id', true)::uuid)
    WITH CHECK (owner_id = current_setting('app.user_id', true)::uuid);

COMMENT ON TABLE editor_users IS
    'Anonymous editor identities. No image bytes, filenames, or EXIF metadata are stored.';
COMMENT ON TABLE editor_projects IS
    'Owner-scoped metadata grouping edit recipes. Image bytes are prohibited.';
COMMENT ON TABLE edit_presets IS
    'Versioned edit recipes using normalized crop coordinates and filter settings only.';

COMMIT;
