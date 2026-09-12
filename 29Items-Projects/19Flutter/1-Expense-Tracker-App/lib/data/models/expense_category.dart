/// Hive typeId 1 — spending category (food, transport, entertainment, ...).
library;

class ExpenseCategory {
  ExpenseCategory({
    required this.id,
    required this.name,
    required this.iconCodePoint,
    required this.colorValue,
    this.isBuiltIn = false,
    this.sortOrder = 0,
  });

  final String id;
  final String name;

  /// Material icon code point. Stored as int so the model needs no Flutter
  /// import — `data/` must stay UI-agnostic.
  ///
  /// NOTE: tree-shaking of icons requires const IconData. The presentation layer
  /// maps these through a lookup table rather than constructing
  /// `IconData(codePoint)` dynamically. See `expense_list_tile.dart`.
  final int iconCodePoint;

  /// ARGB value; see `core/theme/app_colors.dart` for the source palette.
  final int colorValue;

  /// Built-ins are seeded on first run and cannot be deleted (only hidden),
  /// because existing expenses and budgets reference their ids.
  final bool isBuiltIn;

  final int sortOrder;

  ExpenseCategory copyWith({
    String? name,
    int? iconCodePoint,
    int? colorValue,
    int? sortOrder,
  }) {
    return ExpenseCategory(
      id: id,
      name: name ?? this.name,
      iconCodePoint: iconCodePoint ?? this.iconCodePoint,
      colorValue: colorValue ?? this.colorValue,
      isBuiltIn: isBuiltIn,
      sortOrder: sortOrder ?? this.sortOrder,
    );
  }

  @override
  String toString() => 'ExpenseCategory($id, $name)';
}
