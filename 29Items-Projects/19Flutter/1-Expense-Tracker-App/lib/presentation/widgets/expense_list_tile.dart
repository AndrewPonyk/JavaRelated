/// A single expense row, with swipe-to-delete.
///
/// [categoryIconFor] is the lookup table [ExpenseCategory.iconCodePoint]'s doc
/// comment requires: codepoints are mapped to a fixed set of const `Icons.*`
/// references, never built dynamically via `IconData(codePoint)`, which would
/// defeat icon tree-shaking in release builds. Reused by `quick_add_sheet.dart`.
library;

import 'package:flutter/material.dart';

import '../../data/models/expense.dart';
import '../../data/models/expense_category.dart';

/// Maps a known built-in category codepoint (see
/// `AppConstants.builtInCategories`) to its icon. Any other codepoint —
/// including the default assigned to user-created categories — falls back to
/// [Icons.category].
IconData categoryIconFor(int codePoint) {
  switch (codePoint) {
    case 0xe56c:
      return Icons.restaurant;
    case 0xe1d5:
      return Icons.directions_car;
    case 0xe01d:
      return Icons.movie;
    case 0xe0b0:
      return Icons.receipt_long;
    case 0xe1c6:
      return Icons.local_hospital;
    case 0xe5d3:
    default:
      return Icons.category;
  }
}

class ExpenseListTile extends StatelessWidget {
  const ExpenseListTile({
    super.key,
    required this.expense,
    required this.category,
    required this.onTap,
    required this.onDismissed,
  });

  final Expense expense;
  final ExpenseCategory? category;
  final VoidCallback onTap;
  final VoidCallback onDismissed;

  @override
  Widget build(BuildContext context) {
    final color = category != null ? Color(category!.colorValue) : Colors.grey;

    return Dismissible(
      key: ValueKey(expense.id),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        color: Theme.of(context).colorScheme.errorContainer,
        child: Icon(Icons.delete_outline, color: Theme.of(context).colorScheme.onErrorContainer),
      ),
      onDismissed: (_) => onDismissed(),
      child: ListTile(
        onTap: onTap,
        leading: CircleAvatar(
          backgroundColor: color.withValues(alpha: 0.15),
          child: Icon(categoryIconFor(category?.iconCodePoint ?? 0xe5d3), color: color),
        ),
        title: Text(category?.name ?? expense.categoryId),
        subtitle: expense.note == null
            ? (expense.isGenerated ? const Text('Recurring') : null)
            : Text(expense.note!, maxLines: 1, overflow: TextOverflow.ellipsis),
        trailing: Text(
          expense.amount.formatted,
          style: Theme.of(context).textTheme.titleMedium,
        ),
      ),
    );
  }
}
