/// Schema evolution for Hive.
///
/// Hive is schemaless, so there is no DDL and no SQL migration file. What *does*
/// need versioning is **data shape**: backfilling a new field, renaming a
/// category id, re-keying a box, or (Phase 3) moving to encrypted boxes.
///
/// Contract:
///  * Steps are ordered, run once, and must be **idempotent** — a crash halfway
///    through must leave a re-runnable state.
///  * Steps are **additive and backward-tolerant** so a downgraded build can
///    still read the box (TECH-NOTES §3.3 "Rollback reality").
///  * Every step has a matching document in `migrations/`.
library;

import '../../core/constants/app_constants.dart';
import '../../core/constants/hive_boxes.dart';
import '../../core/error/failures.dart';
import '../../core/logging/app_logger.dart';
import '../../core/utils/result.dart';
import '../datasources/hive_service.dart';
import '../models/app_settings.dart';
import '../models/expense_category.dart';

typedef MigrationStep = Future<void> Function(HiveService hive);

class HiveMigrator {
  HiveMigrator(this._hive);

  final HiveService _hive;
  final AppLogger _log = AppLogger('HiveMigrator');

  /// Keyed by the version the step migrates **to**.
  ///
  /// Version 1 is the initial schema: seed built-in categories and write the
  /// settings singleton. Add `2: _v2AddSomething` etc. below.
  Map<int, MigrationStep> get _steps => <int, MigrationStep>{
        1: _v1InitialSeed,
        // 2: _v2ExampleAddField,  // see migrations/0002_example_add_field.md
      };

  Future<Result<int>> run() async {
    try {
      final settingsBox = _hive.settings;
      final current = settingsBox.get(SettingsKeys.singleton);
      var version = current?.schemaVersion ?? 0;

      // A brand-new install has no settings row at all → version 0.
      if (current == null) version = 0;

      if (version > AppConstants.schemaVersion) {
        // Downgrade: the box was written by a newer build. Adapters are
        // forward-tolerant, so continue rather than refuse — but say so loudly.
        _log.warn('box schema is newer than this build', {
          'boxVersion': version,
          'buildVersion': AppConstants.schemaVersion,
        });
        return Ok(version);
      }

      for (var v = version + 1; v <= AppConstants.schemaVersion; v++) {
        final step = _steps[v];
        if (step == null) {
          _log.warn('no migration step registered', {'version': v});
          continue;
        }
        _log.info('running migration', {'to': v});
        await step(_hive);
      }

      final latest = settingsBox.get(SettingsKeys.singleton) ?? AppSettings();
      await settingsBox.put(
        SettingsKeys.singleton,
        latest.copyWith(schemaVersion: AppConstants.schemaVersion),
      );

      _log.info('schema up to date', {'version': AppConstants.schemaVersion});
      return Ok(AppConstants.schemaVersion);
    } catch (error, stack) {
      _log.error('migration failed', cause: error, stackTrace: stack);
      return Err(StorageFailure(
        'Schema migration failed',
        cause: error,
        stackTrace: stack,
      ));
    }
  }

  /// v1 — initial schema. Idempotent: guarded by `categoriesSeeded`, and each
  /// `put` is keyed by a stable id so re-running overwrites rather than
  /// duplicates.
  Future<void> _v1InitialSeed(HiveService hive) async {
    final settingsBox = hive.settings;
    final settings = settingsBox.get(SettingsKeys.singleton) ?? AppSettings();

    if (!settings.categoriesSeeded || hive.categories.isEmpty) {
      var order = 0;
      for (final c in AppConstants.builtInCategories) {
        await hive.categories.put(
          c.id,
          ExpenseCategory(
            id: c.id,
            name: c.name,
            iconCodePoint: c.icon,
            colorValue: c.color,
            isBuiltIn: true,
            sortOrder: order++,
          ),
        );
      }
      _log.info('seeded built-in categories', {'count': order});
    }

    await settingsBox.put(
      SettingsKeys.singleton,
      settings.copyWith(categoriesSeeded: true, schemaVersion: 1),
    );
  }

  // Worked example of a future step — see migrations/0002_example_add_field.md.
  //
  // Future<void> _v2ExampleAddField(HiveService hive) async {
  //   // Adding a nullable field needs NO data migration: the adapter defaults it
  //   // on read. A step is only required when you must BACKFILL a value or
  //   // re-key/relocate existing rows.
  //   for (final key in hive.expenses.keys) {
  //     final e = hive.expenses.get(key);
  //     if (e == null) continue;
  //     await hive.expenses.put(key, e.copyWith(/* backfilled field */));
  //   }
  // }
}
