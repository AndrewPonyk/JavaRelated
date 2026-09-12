/// Hive lifecycle: init, adapter registration, box opening, compaction, close.
///
/// The only place in the app that calls `Hive.openBox`. Repositories receive
/// already-open boxes, which is what lets tests point Hive at a temp directory
/// without any mocking (docs/TECH-NOTES.md §3.2).
library;

import 'package:hive_flutter/hive_flutter.dart';

import '../../core/config/env.dart';
import '../../core/constants/hive_boxes.dart';
import '../../core/logging/app_logger.dart';
import '../adapters/hive_adapters.dart';
import '../models/app_settings.dart';
import '../models/budget.dart';
import '../models/currency_rate.dart';
import '../models/expense.dart';
import '../models/expense_category.dart';
import '../models/quick_template.dart';
import '../models/recurring_expense.dart';

class HiveService {
  HiveService();

  final AppLogger _log = AppLogger('HiveService');
  bool _initialised = false;

  /// Initialise Hive and open the boxes needed before the first frame.
  ///
  /// Only `settings` and `categories` are opened eagerly — the cold-start budget
  /// is 400ms (ARCHITECTURE §2.4). Everything else is opened on demand by
  /// [ensureOpen].
  Future<void> init({bool eagerOnly = true}) async {
    if (_initialised) return;
    await Hive.initFlutter();
    registerHiveAdapters();

    if (Env.encryptBoxes) {
      // TODO(phase3): read a 256-bit key from Keystore/Keychain via
      // flutter_secure_storage and pass HiveAesCipher(key) to every openBox.
      // Enabling this on an existing install needs a migration step:
      // read plaintext → write encrypted → delete plaintext. ARCHITECTURE §2.5.
      _log.warn('ENCRYPT_BOXES is set but encryption is not implemented yet');
    }

    final toOpen = eagerOnly ? HiveBoxes.eager : HiveBoxes.all;
    for (final name in toOpen) {
      await _open(name);
    }

    _initialised = true;
    _log.info('Hive initialised', {'opened': toOpen.length});
  }

  Future<void> _open(String name) async {
    if (Hive.isBoxOpen(name)) return;
    switch (name) {
      case HiveBoxes.expenses:
        await Hive.openBox<Expense>(name, compactionStrategy: _compaction);
      case HiveBoxes.categories:
        await Hive.openBox<ExpenseCategory>(name);
      case HiveBoxes.budgets:
        await Hive.openBox<Budget>(name);
      case HiveBoxes.recurring:
        await Hive.openBox<RecurringExpense>(name);
      case HiveBoxes.templates:
        await Hive.openBox<QuickTemplate>(name);
      case HiveBoxes.settings:
        await Hive.openBox<AppSettings>(name);
      case HiveBoxes.rates:
        await Hive.openBox<CurrencyRate>(name);
      default:
        throw StateError('Unknown box "$name" — add it to HiveBoxes.all');
    }
    _log.debug('box opened', {'box': name});
  }

  /// Opens any not-yet-open boxes. Call after the first frame.
  Future<void> ensureOpen(List<String> names) async {
    for (final n in names) {
      await _open(n);
    }
  }

  /// Compact once the box has accumulated more deleted entries than live ones,
  /// with a floor so tiny boxes never bother. Hive appends on write, so without
  /// compaction a heavily edited box grows unboundedly.
  static bool _compaction(int entries, int deletedEntries) =>
      deletedEntries > 50 && deletedEntries > entries ~/ 2;

  Box<Expense> get expenses => Hive.box<Expense>(HiveBoxes.expenses);
  Box<ExpenseCategory> get categories =>
      Hive.box<ExpenseCategory>(HiveBoxes.categories);
  Box<Budget> get budgets => Hive.box<Budget>(HiveBoxes.budgets);
  Box<RecurringExpense> get recurring =>
      Hive.box<RecurringExpense>(HiveBoxes.recurring);
  Box<QuickTemplate> get templates => Hive.box<QuickTemplate>(HiveBoxes.templates);
  Box<AppSettings> get settings => Hive.box<AppSettings>(HiveBoxes.settings);
  Box<CurrencyRate> get rates => Hive.box<CurrencyRate>(HiveBoxes.rates);

  /// Always call in test `tearDown` — leaked open boxes contaminate later tests
  /// in confusing ways (docs/TECH-NOTES.md §3.6).
  Future<void> close() async {
    await Hive.close();
    _initialised = false;
  }

  /// Destructive. Exposed for the settings screen's "erase all data" action and
  /// for tests. Requires an explicit user confirmation in the UI.
  Future<void> deleteAllData() async {
    for (final name in HiveBoxes.all) {
      if (Hive.isBoxOpen(name)) {
        await Hive.box(name).clear();
      }
    }
    _log.warn('all local data cleared by user request');
  }
}
