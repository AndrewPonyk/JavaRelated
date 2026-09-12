/// Hand-written Hive [TypeAdapter]s.
///
/// These emit **byte-for-byte the same wire format** as `hive_generator`:
/// `writeByte(fieldCount)` followed by `(fieldIndex, value)` pairs. Writing them
/// by hand removes `build_runner` from the toolchain entirely — no `.g.dart` in
/// diffs, no codegen step in CI, green `flutter analyze` on a fresh clone.
/// PROJECT-PLAN §1.3(a) covers the trade-off.
///
/// ## Rules when editing this file
///  1. **Field indices are permanent.** Add a field → use the next unused index.
///     Never renumber, never reuse a retired index.
///  2. **Bump `fieldCount`** in `write` when you add a field, or the reader will
///     stop early and silently drop data.
///  3. `read` must tolerate a **missing** index (older row → use a default) and
///     an **unknown** index (newer row read by older code → ignored by the map).
///     This is what makes downgrades non-destructive — see TECH-NOTES §3.3.
///  4. Update `migrations/000N_*.md` in the same commit.
library;

import 'package:hive/hive.dart';

import '../../core/constants/app_constants.dart';
import '../../core/constants/hive_boxes.dart';
import '../models/app_settings.dart';
import '../models/budget.dart';
import '../models/currency_rate.dart';
import '../models/expense.dart';
import '../models/expense_category.dart';
import '../models/quick_template.dart';
import '../models/recurring_expense.dart';

/// Reads the `(index → value)` map that every adapter below starts from.
Map<int, dynamic> _readFields(BinaryReader reader) {
  final count = reader.readByte();
  return <int, dynamic>{
    for (var i = 0; i < count; i++) reader.readByte(): reader.read(),
  };
}

// ---------------------------------------------------------------------------
// typeId 0 — Expense
// ---------------------------------------------------------------------------
class ExpenseAdapter extends TypeAdapter<Expense> {
  @override
  final int typeId = HiveTypeIds.expense;

  @override
  Expense read(BinaryReader reader) {
    final f = _readFields(reader);
    return Expense(
      id: f[0] as String,
      amountMinor: f[1] as int,
      currencyCode: f[2] as String? ?? AppConstants.defaultCurrency,
      categoryId: f[3] as String,
      date: f[4] as DateTime,
      createdAt: f[5] as DateTime? ?? f[4] as DateTime,
      note: f[6] as String?,
      recurringId: f[7] as String?,
    );
  }

  @override
  void write(BinaryWriter writer, Expense obj) {
    writer
      ..writeByte(8)
      ..writeByte(0)
      ..write(obj.id)
      ..writeByte(1)
      ..write(obj.amountMinor)
      ..writeByte(2)
      ..write(obj.currencyCode)
      ..writeByte(3)
      ..write(obj.categoryId)
      ..writeByte(4)
      ..write(obj.date)
      ..writeByte(5)
      ..write(obj.createdAt)
      ..writeByte(6)
      ..write(obj.note)
      ..writeByte(7)
      ..write(obj.recurringId);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is ExpenseAdapter && other.typeId == typeId;
}

// ---------------------------------------------------------------------------
// typeId 1 — ExpenseCategory
// ---------------------------------------------------------------------------
class ExpenseCategoryAdapter extends TypeAdapter<ExpenseCategory> {
  @override
  final int typeId = HiveTypeIds.expenseCategory;

  @override
  ExpenseCategory read(BinaryReader reader) {
    final f = _readFields(reader);
    return ExpenseCategory(
      id: f[0] as String,
      name: f[1] as String,
      iconCodePoint: f[2] as int? ?? 0xe5d3,
      colorValue: f[3] as int? ?? 0xFF9C755F,
      isBuiltIn: f[4] as bool? ?? false,
      sortOrder: f[5] as int? ?? 0,
    );
  }

  @override
  void write(BinaryWriter writer, ExpenseCategory obj) {
    writer
      ..writeByte(6)
      ..writeByte(0)
      ..write(obj.id)
      ..writeByte(1)
      ..write(obj.name)
      ..writeByte(2)
      ..write(obj.iconCodePoint)
      ..writeByte(3)
      ..write(obj.colorValue)
      ..writeByte(4)
      ..write(obj.isBuiltIn)
      ..writeByte(5)
      ..write(obj.sortOrder);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ExpenseCategoryAdapter && other.typeId == typeId;
}

// ---------------------------------------------------------------------------
// typeId 2 — Budget
// ---------------------------------------------------------------------------
class BudgetAdapter extends TypeAdapter<Budget> {
  @override
  final int typeId = HiveTypeIds.budget;

  @override
  Budget read(BinaryReader reader) {
    final f = _readFields(reader);
    return Budget(
      id: f[0] as String,
      month: f[1] as DateTime,
      limitMinor: f[2] as int,
      currencyCode: f[3] as String? ?? AppConstants.defaultCurrency,
      categoryId: f[4] as String?,
      alertThreshold:
          (f[5] as num?)?.toDouble() ?? AppConstants.defaultBudgetAlertThreshold,
    );
  }

  @override
  void write(BinaryWriter writer, Budget obj) {
    writer
      ..writeByte(6)
      ..writeByte(0)
      ..write(obj.id)
      ..writeByte(1)
      ..write(obj.month)
      ..writeByte(2)
      ..write(obj.limitMinor)
      ..writeByte(3)
      ..write(obj.currencyCode)
      ..writeByte(4)
      ..write(obj.categoryId)
      ..writeByte(5)
      ..write(obj.alertThreshold);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is BudgetAdapter && other.typeId == typeId;
}

// ---------------------------------------------------------------------------
// typeId 7 — RecurrenceFrequency (enum stored as index)
// ---------------------------------------------------------------------------
class RecurrenceFrequencyAdapter extends TypeAdapter<RecurrenceFrequency> {
  @override
  final int typeId = HiveTypeIds.recurrenceFrequency;

  @override
  RecurrenceFrequency read(BinaryReader reader) {
    final index = reader.readByte();
    // Forward tolerance: a newer build may have appended enum values. Fall back
    // rather than crash on an out-of-range index (TECH-NOTES §3.3).
    if (index < 0 || index >= RecurrenceFrequency.values.length) {
      return RecurrenceFrequency.monthly;
    }
    return RecurrenceFrequency.values[index];
  }

  @override
  void write(BinaryWriter writer, RecurrenceFrequency obj) {
    writer.writeByte(obj.index);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is RecurrenceFrequencyAdapter && other.typeId == typeId;
}

// ---------------------------------------------------------------------------
// typeId 3 — RecurringExpense
// ---------------------------------------------------------------------------
class RecurringExpenseAdapter extends TypeAdapter<RecurringExpense> {
  @override
  final int typeId = HiveTypeIds.recurringExpense;

  @override
  RecurringExpense read(BinaryReader reader) {
    final f = _readFields(reader);
    return RecurringExpense(
      id: f[0] as String,
      amountMinor: f[1] as int,
      currencyCode: f[2] as String? ?? AppConstants.defaultCurrency,
      categoryId: f[3] as String,
      frequency: f[4] as RecurrenceFrequency? ?? RecurrenceFrequency.monthly,
      startDate: f[5] as DateTime,
      note: f[6] as String?,
      endDate: f[7] as DateTime?,
      lastGeneratedDate: f[8] as DateTime?,
      isActive: f[9] as bool? ?? true,
    );
  }

  @override
  void write(BinaryWriter writer, RecurringExpense obj) {
    writer
      ..writeByte(10)
      ..writeByte(0)
      ..write(obj.id)
      ..writeByte(1)
      ..write(obj.amountMinor)
      ..writeByte(2)
      ..write(obj.currencyCode)
      ..writeByte(3)
      ..write(obj.categoryId)
      ..writeByte(4)
      ..write(obj.frequency)
      ..writeByte(5)
      ..write(obj.startDate)
      ..writeByte(6)
      ..write(obj.note)
      ..writeByte(7)
      ..write(obj.endDate)
      ..writeByte(8)
      ..write(obj.lastGeneratedDate)
      ..writeByte(9)
      ..write(obj.isActive);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is RecurringExpenseAdapter && other.typeId == typeId;
}

// ---------------------------------------------------------------------------
// typeId 4 — QuickTemplate
// ---------------------------------------------------------------------------
class QuickTemplateAdapter extends TypeAdapter<QuickTemplate> {
  @override
  final int typeId = HiveTypeIds.quickTemplate;

  @override
  QuickTemplate read(BinaryReader reader) {
    final f = _readFields(reader);
    return QuickTemplate(
      id: f[0] as String,
      label: f[1] as String,
      amountMinor: f[2] as int,
      currencyCode: f[3] as String? ?? AppConstants.defaultCurrency,
      categoryId: f[4] as String,
      iconCodePoint: f[5] as int?,
      useCount: f[6] as int? ?? 0,
      lastUsedAt: f[7] as DateTime?,
    );
  }

  @override
  void write(BinaryWriter writer, QuickTemplate obj) {
    writer
      ..writeByte(8)
      ..writeByte(0)
      ..write(obj.id)
      ..writeByte(1)
      ..write(obj.label)
      ..writeByte(2)
      ..write(obj.amountMinor)
      ..writeByte(3)
      ..write(obj.currencyCode)
      ..writeByte(4)
      ..write(obj.categoryId)
      ..writeByte(5)
      ..write(obj.iconCodePoint)
      ..writeByte(6)
      ..write(obj.useCount)
      ..writeByte(7)
      ..write(obj.lastUsedAt);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is QuickTemplateAdapter && other.typeId == typeId;
}

// ---------------------------------------------------------------------------
// typeId 5 — AppSettings
// ---------------------------------------------------------------------------
class AppSettingsAdapter extends TypeAdapter<AppSettings> {
  @override
  final int typeId = HiveTypeIds.appSettings;

  @override
  AppSettings read(BinaryReader reader) {
    final f = _readFields(reader);
    return AppSettings(
      baseCurrency: f[0] as String? ?? AppConstants.defaultCurrency,
      budgetAlertThreshold:
          (f[1] as num?)?.toDouble() ?? AppConstants.defaultBudgetAlertThreshold,
      notificationsEnabled: f[2] as bool? ?? true,
      monthlyRolloverReminder: f[3] as bool? ?? true,
      themeModeIndex: f[4] as int? ?? 0,
      weekStartsOnMonday: f[5] as bool? ?? true,
      schemaVersion: f[6] as int? ?? 1,
      categoriesSeeded: f[7] as bool? ?? false,
      lastRecurringRunAt: f[8] as DateTime?,
    );
  }

  @override
  void write(BinaryWriter writer, AppSettings obj) {
    writer
      ..writeByte(9)
      ..writeByte(0)
      ..write(obj.baseCurrency)
      ..writeByte(1)
      ..write(obj.budgetAlertThreshold)
      ..writeByte(2)
      ..write(obj.notificationsEnabled)
      ..writeByte(3)
      ..write(obj.monthlyRolloverReminder)
      ..writeByte(4)
      ..write(obj.themeModeIndex)
      ..writeByte(5)
      ..write(obj.weekStartsOnMonday)
      ..writeByte(6)
      ..write(obj.schemaVersion)
      ..writeByte(7)
      ..write(obj.categoriesSeeded)
      ..writeByte(8)
      ..write(obj.lastRecurringRunAt);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is AppSettingsAdapter && other.typeId == typeId;
}

// ---------------------------------------------------------------------------
// typeId 6 — CurrencyRate
// ---------------------------------------------------------------------------
class CurrencyRateAdapter extends TypeAdapter<CurrencyRate> {
  @override
  final int typeId = HiveTypeIds.currencyRate;

  @override
  CurrencyRate read(BinaryReader reader) {
    final f = _readFields(reader);
    return CurrencyRate(
      code: f[0] as String,
      baseCode: f[1] as String? ?? AppConstants.defaultCurrency,
      rateToBase: (f[2] as num?)?.toDouble() ?? 1.0,
      updatedAt: f[3] as DateTime? ?? DateTime.fromMillisecondsSinceEpoch(0),
      isManual: f[4] as bool? ?? false,
    );
  }

  @override
  void write(BinaryWriter writer, CurrencyRate obj) {
    writer
      ..writeByte(5)
      ..writeByte(0)
      ..write(obj.code)
      ..writeByte(1)
      ..write(obj.baseCode)
      ..writeByte(2)
      ..write(obj.rateToBase)
      ..writeByte(3)
      ..write(obj.updatedAt)
      ..writeByte(4)
      ..write(obj.isManual);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CurrencyRateAdapter && other.typeId == typeId;
}

/// Registers every adapter exactly once. Called by [HiveService.init].
///
/// Registering twice throws in Hive, so this guards with `isAdapterRegistered` —
/// which also makes it safe to call from each test's `setUp`.
void registerHiveAdapters() {
  void reg<T>(TypeAdapter<T> adapter) {
    if (!Hive.isAdapterRegistered(adapter.typeId)) {
      Hive.registerAdapter<T>(adapter);
    }
  }

  reg(ExpenseAdapter());
  reg(ExpenseCategoryAdapter());
  reg(BudgetAdapter());
  reg(RecurringExpenseAdapter());
  reg(QuickTemplateAdapter());
  reg(AppSettingsAdapter());
  reg(CurrencyRateAdapter());
  reg(RecurrenceFrequencyAdapter());
}
