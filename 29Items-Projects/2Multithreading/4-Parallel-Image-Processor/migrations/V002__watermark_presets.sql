-- ============================================================================
--  V002__watermark_presets.sql — reusable watermark and pipeline presets
--
--  Why presets are in the database rather than in a config file: they are user
--  data. Someone spends ten minutes getting the opacity and margin of their
--  studio's copyright line right, and they expect it back tomorrow. Config
--  files are for machine settings the operator edits by hand (see
--  config/application.properties); this is content the UI writes.
--
--  Columns mirror ImageOperation.Watermark exactly, so the adapter maps a row to
--  the record with no interpretation. When that record gains a component, add a
--  V004 rather than editing this file.
-- ============================================================================

CREATE TABLE IF NOT EXISTS watermark_presets (
    preset_id    INTEGER PRIMARY KEY,          -- rowid alias: SQLite fills it in
    name         TEXT    NOT NULL UNIQUE,      -- shown in the UI's dropdown
    text         TEXT,                         -- NULL for an overlay-only preset
    overlay_path TEXT,                         -- NULL for a text-only preset
    anchor       TEXT    NOT NULL DEFAULT 'BOTTOM_RIGHT',
    opacity      REAL    NOT NULL DEFAULT 0.55,
    margin_px    INTEGER NOT NULL DEFAULT 24,
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL,

    -- Mirrors the Watermark record's own constructor check. Enforcing it here
    -- too means a hand-written INSERT during support work cannot create a
    -- preset that blows up at load time with an IllegalArgumentException.
    CHECK (
        (text IS NOT NULL AND trim(text) <> '')
        OR (overlay_path IS NOT NULL AND trim(overlay_path) <> '')
    ),
    CHECK (anchor IN ('TOP_LEFT', 'TOP_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_RIGHT', 'CENTER')),
    CHECK (opacity BETWEEN 0.0 AND 1.0),
    CHECK (margin_px >= 0)
) STRICT;

-- ----------------------------------------------------------------------------
--  pipeline_presets — a named operation chain, e.g. "web thumbnail"
--
--  The chain is stored as the same compact textual form the batches table uses
--  ('grayscale>resize:400x400>sharpen:1.2'). A normalized child table would be
--  the textbook answer, but the pipeline is always read and written whole, never
--  queried by stage, so a join per read would buy nothing. The syntax has exactly
--  one implementation, core's PipelineFormat, which both the CLI and this table's
--  readers go through.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pipeline_presets (
    preset_id     INTEGER PRIMARY KEY,
    name          TEXT    NOT NULL UNIQUE,
    pipeline      TEXT    NOT NULL,
    output_format TEXT    NOT NULL DEFAULT 'png',
    quality       REAL    NOT NULL DEFAULT 0.9,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,

    -- No CHECK that the pipeline is non-empty. An empty chain is a legal preset:
    -- it means "decode and re-encode", which is how a user converts a directory of
    -- TIFFs to PNG without touching the pixels. ProcessingOptions documents the
    -- same thing ("operations may be empty (a pure format conversion)") and
    -- PipelineFormat.render(List.of()) is the empty string, so rejecting it here
    -- would make a preset the rest of the system considers valid unstorable.
    CHECK (quality BETWEEN 0.0 AND 1.0)
) STRICT;

-- ----------------------------------------------------------------------------
--  Seed data
--
--  ON CONFLICT (name) DO NOTHING, so re-running this migration on a database that
--  already has it (which the runner will not do, but a support script might) is a
--  no-op rather than a constraint violation. Deliberately not DO UPDATE: if a user
--  has edited "Studio copyright", their version wins.
--
--  Note the conflict target. The broader `INSERT OR IGNORE` would have read more
--  neatly, but it ignores *every* constraint failure, including the CHECKs above --
--  so a seed row that violated one would be dropped in silence and the migration
--  would report success with a preset missing. That is not hypothetical: it is
--  exactly how the "Archive convert" row disappeared until a test counted the rows.
--  Naming (name) means only a duplicate is tolerated; anything else fails the
--  migration inside its transaction, which is what a broken seed deserves.
--
--  Timestamps are literal 0 rather than a call to unixepoch(): a migration must
--  produce byte-identical results whenever it runs, and the runner checksums the
--  file. 0 reads as "shipped with the application, not created by you".
-- ----------------------------------------------------------------------------
INSERT INTO watermark_presets
    (name, text, overlay_path, anchor, opacity, margin_px, created_at, updated_at)
VALUES
    ('Studio copyright', '(c) My Studio', NULL, 'BOTTOM_RIGHT', 0.55, 24, 0, 0),
    ('Subtle corner',    'PREVIEW',       NULL, 'BOTTOM_LEFT',  0.30, 16, 0, 0),
    ('Centred proof',    'PROOF',         NULL, 'CENTER',       0.20, 0,  0, 0)
ON CONFLICT (name) DO NOTHING;

INSERT INTO pipeline_presets
    (name, pipeline, output_format, quality, created_at, updated_at)
VALUES
    ('Web thumbnail',   'resize:400x400>sharpen:1.1', 'jpg', 0.82, 0, 0),
    ('Contact sheet',   'grayscale>resize:200x200',   'png', 0.90, 0, 0),
    -- Empty pipeline: a pure format conversion. See the CHECK note on the table.
    ('Archive convert', '',                           'png', 1.00, 0, 0)
ON CONFLICT (name) DO NOTHING;
