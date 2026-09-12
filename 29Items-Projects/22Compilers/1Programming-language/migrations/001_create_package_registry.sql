CREATE TABLE package_namespaces (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE packages (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT REFERENCES package_namespaces(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    description TEXT,
    repository_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (namespace_id, name)
);

CREATE TABLE package_versions (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    version TEXT NOT NULL,
    manifest_json JSONB NOT NULL,
    archive_sha256 TEXT NOT NULL,
    yanked_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (package_id, version)
);

CREATE TABLE package_dependencies (
    id BIGSERIAL PRIMARY KEY,
    package_version_id BIGINT NOT NULL REFERENCES package_versions(id) ON DELETE CASCADE,
    dependency_name TEXT NOT NULL,
    version_constraint TEXT NOT NULL,
    optional BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE package_audit_events (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT REFERENCES packages(id) ON DELETE SET NULL,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_package_versions_package_id ON package_versions(package_id);
CREATE INDEX idx_package_dependencies_version_id ON package_dependencies(package_version_id);
CREATE INDEX idx_package_dependencies_name ON package_dependencies(dependency_name);
