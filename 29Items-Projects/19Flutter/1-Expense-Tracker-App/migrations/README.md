# Hive "Migrations" — How This Differs From SQL

Hive is a schemaless key-value store. There is no `ALTER TABLE`, no DDL, and nothing here runs
against a database server. What we call a "migration" is a **versioned code step** that runs once
against the boxes already on the device, plus a **frozen, append-only registry** of type IDs and
field indices that the hand-written adapters in `lib/data/adapters/hive_adapters.dart` depend on.

## Two different kinds of change

| SQL world | Hive world here |
|---|---|
| `ALTER TABLE ADD COLUMN` | Add a nullable field with the next unused index; adapter defaults it on read. **No migration step needed** — see `migrations/0002_example_add_field.md`. |
| Backfilling a computed column | A `MigrationStep` in `hive_migrator.dart` that reads every row and rewrites it. |
| Renaming a table | Not supported — box names are permanent (`hive_boxes.dart` header). Ship a new box + a one-time copy step instead, and never delete the old typeId. |
| `DROP COLUMN` | Never actually drop the index. Stop writing it, keep the reader tolerant of it being present in old rows, and document the index as retired. |

## The two registries that matter

1. **`lib/core/constants/hive_boxes.dart`** — box names and `typeId`s. Append-only. This file's own
   header comment is the enforcement mechanism: read it before touching this directory.
2. **`lib/data/adapters/hive_adapters.dart`** — the hand-written `read`/`write` pair for each model.
   Field indices inside each adapter are just as permanent as the typeId itself.

Every entry in those two files must have a matching `.md` here describing what it means and why. This
directory is the source of truth for *why* a given index holds what it holds — the code tells you
*what*, the docs here tell you *why*, which matters most exactly when someone is deciding whether a
change is safe.

## Adding a new migration doc

1. Bump `AppConstants.schemaVersion` (`lib/core/constants/app_constants.dart`).
2. Add the step to `HiveMigrator._steps`, keyed by the version it migrates **to**.
3. Write `000N_<short-description>.md` here, following the shape of `0001_initial_schema.md`.
4. If you added a model or field, update `hive_boxes.dart`'s `HiveTypeIds.nextFree` in the same commit.

## Rules (mirrors `hive_boxes.dart` and `hive_adapters.dart` headers)

- **typeIds are permanent.** Reusing one for a different class silently deserialises garbage.
- **Field indices are permanent.** A removed field's index is retired, never reused, never shifted.
- **Steps must be idempotent.** A crash mid-step must leave a state the step can safely re-run against.
- **Steps must be additive and backward-tolerant** (docs/TECH-NOTES.md §3.3 "Rollback reality") — an
  older build reading a newer box must not crash. Unknown indices are ignored; missing indices default.
