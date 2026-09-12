"""Unit tests for the migration SQL splitter (no database required)."""

from __future__ import annotations

from churn_predictor.db.migrate import MIGRATIONS_DIR, _split_statements


def test_split_drops_comments_and_transaction_wrappers() -> None:
    sql = """
    BEGIN;
    -- a full-line comment
    CREATE TABLE foo (id INT);  -- trailing comment kept
    COMMIT;
    """
    statements = _split_statements(sql)
    assert len(statements) == 1
    assert statements[0].startswith("CREATE TABLE foo")
    assert all(s.lower() not in {"begin", "commit"} for s in statements)


def test_split_ignores_fully_commented_block() -> None:
    sql = """
    CREATE INDEX idx ON foo (id);
    -- ALTER TABLE foo
    --   ADD CONSTRAINT fk FOREIGN KEY (id) REFERENCES bar (id);
    """
    statements = _split_statements(sql)
    # Only the real CREATE INDEX statement should remain.
    assert len(statements) == 1
    assert "CREATE INDEX" in statements[0]


def test_real_migration_files_parse() -> None:
    files = sorted(MIGRATIONS_DIR.glob("*.sql"))
    assert files, "no migration files found"
    for path in files:
        statements = _split_statements(path.read_text(encoding="utf-8"))
        assert statements, f"{path.name} produced no statements"
        assert any("CREATE TABLE" in s for s in statements)
