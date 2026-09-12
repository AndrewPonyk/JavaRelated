# 0002 — Worked Example: Adding a Field (Not Shipped)

**Status:** template only — not registered in `HiveMigrator._steps`, no schema version bump. This
document exists purely so the *next* real schema change has a pattern to copy, and so the commented-out
`_v2ExampleAddField` stub in `hive_migrator.dart` has a written rationale next to it instead of just a
code comment.

## The two cases, and why they need different amounts of work

### Case A — additive field with a sensible default (no migration step needed)

Say we add `Expense.isPending` (`bool`, for a not-yet-cleared card transaction). Steps:

1. Add the field to the `Expense` model (`lib/data/models/expense.dart`) and its `copyWith`.
2. In `ExpenseAdapter.write`, bump `writeByte(8)` → `writeByte(9)` and append
   `..writeByte(8) ..write(obj.isPending)`.
3. In `ExpenseAdapter.read`, add `isPending: f[8] as bool? ?? false,`.
4. Update `HiveTypeIds.nextFree` comment if this introduced a new class (it doesn't, here).
5. Update `migrations/0001_initial_schema.md`'s `Expense` field table to add index 8.

**No `MigrationStep` is required.** An *old* row simply has no index 8 in its byte stream; `f[8]`
evaluates to `null`, and `?? false` supplies the default. This is why the "default on missing" column
in `0001_initial_schema.md` exists — every field needs one, precisely so this case stays free.

### Case B — backfilling a computed value, or re-keying (a migration step IS needed)

Say a later requirement needs every existing `Expense` to have `note` populated from a legacy
`autoNote` heuristic instead of staying `null`. A missing-index default cannot do this — it has to
read existing rows and rewrite them:

```dart
// In hive_migrator.dart, uncomment and register as `2: _v2BackfillNotes`.
Future<void> _v2BackfillNotes(HiveService hive) async {
  for (final key in hive.expenses.keys) {
    final e = hive.expenses.get(key);
    if (e == null || e.note != null) continue; // idempotent: skip already-migrated rows
    await hive.expenses.put(key, e.copyWith(note: _deriveNoteFrom(e)));
  }
}
```

Then:

1. Bump `AppConstants.schemaVersion` to `2`.
2. Register the step: `_steps[2] = _v2BackfillNotes;`.
3. Write `migrations/0003_backfill_notes.md` describing the *why*, not just the *what* — this file is
   the example of "what", the "why" belongs in a real doc for a real change.

## Rules this example is demonstrating

- **Idempotent**: the `if (e.note != null) continue;` guard means running the step twice (e.g. after a
  crash mid-migration) does not re-derive or double-write anything.
- **Additive, not destructive**: nothing is deleted; an older build reading this box afterward simply
  sees the new `note` values as ordinary data — it doesn't need to understand *why* they're populated.
- **Field index, not schema version, drives adapter tolerance.** The schema version in `AppSettings`
  only gates *which migration steps have run*; the adapter's own missing/unknown-index handling is what
  actually makes the box readable across versions.
