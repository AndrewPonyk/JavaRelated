/// Shared temp-Hive bootstrap for tests — see docs/TECH-NOTES.md §3.2 "The
/// Hive testing decision": real Hive in a temp directory, never mocked.
///
/// NOTE: inside a `testWidgets` body, real Hive I/O hangs unless wrapped in
/// `tester.runAsync(...)` — `testWidgets` callbacks run inside flutter_test's
/// FakeAsync zone, which never drives the real event loop that real disk
/// writes depend on to resolve (see `test/widget_test.dart`). Plain `test()`
/// bodies (every unit test in `test/unit/`) run in the real zone and do not
/// need `runAsync`.
library;

import 'dart:io';

import 'package:hive_flutter/hive_flutter.dart';

import 'package:expense_tracker/core/constants/hive_boxes.dart';
import 'package:expense_tracker/data/adapters/hive_adapters.dart';
import 'package:expense_tracker/data/datasources/hive_service.dart';
import 'package:expense_tracker/data/models/app_settings.dart';
import 'package:expense_tracker/data/models/budget.dart';
import 'package:expense_tracker/data/models/currency_rate.dart';
import 'package:expense_tracker/data/models/expense.dart';
import 'package:expense_tracker/data/models/expense_category.dart';
import 'package:expense_tracker/data/models/quick_template.dart';
import 'package:expense_tracker/data/models/recurring_expense.dart';

/// Opens every box against a fresh temp directory and hands back a ready
/// [HiveService]. Always call [dispose] in `tearDown`, or leaked open boxes
/// contaminate later tests (docs/TECH-NOTES.md §3.6).
class HiveTestHarness {
  HiveTestHarness._(this.hive, this._tempDir);

  final HiveService hive;
  final Directory _tempDir;

  static Future<HiveTestHarness> open() async {
    final tempDir = await Directory.systemTemp.createTemp('expense_tracker_test_');
    Hive.init(tempDir.path);
    registerHiveAdapters();
    // Opened with each model's real type (not `hive.init()`, which would call
    // `Hive.initFlutter()` and reach for the unmocked path_provider channel) —
    // matches HiveService._open, so HiveService's getters find boxes of the
    // type they expect.
    await Hive.openBox<Expense>(HiveBoxes.expenses);
    await Hive.openBox<ExpenseCategory>(HiveBoxes.categories);
    await Hive.openBox<Budget>(HiveBoxes.budgets);
    await Hive.openBox<RecurringExpense>(HiveBoxes.recurring);
    await Hive.openBox<QuickTemplate>(HiveBoxes.templates);
    await Hive.openBox<AppSettings>(HiveBoxes.settings);
    await Hive.openBox<CurrencyRate>(HiveBoxes.rates);
    return HiveTestHarness._(HiveService(), tempDir);
  }

  Future<void> dispose() async {
    await Hive.deleteFromDisk();
    await _tempDir.delete(recursive: true);
  }
}
