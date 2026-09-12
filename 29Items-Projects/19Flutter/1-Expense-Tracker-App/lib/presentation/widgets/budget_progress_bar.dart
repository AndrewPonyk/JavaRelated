/// One row on the budget screen. Safe/warning/over is always carried by an
/// icon and a label, never colour alone (docs/TECH-NOTES.md §3.6).
library;

import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../domain/services/budget_service.dart';

class BudgetProgressBar extends StatelessWidget {
  const BudgetProgressBar({
    super.key,
    required this.progress,
    this.label,
    this.onTap,
  });

  final BudgetProgress progress;

  /// Category name, or `null`/"Overall" for the whole-month budget.
  final String? label;
  final VoidCallback? onTap;

  static const Map<BudgetStatus, Color> _colors = {
    BudgetStatus.safe: AppColors.budgetSafe,
    BudgetStatus.warning: AppColors.budgetWarning,
    BudgetStatus.over: AppColors.budgetOver,
  };

  static const Map<BudgetStatus, IconData> _icons = {
    BudgetStatus.safe: Icons.check_circle_outline,
    BudgetStatus.warning: Icons.warning_amber_rounded,
    BudgetStatus.over: Icons.error_outline,
  };

  static const Map<BudgetStatus, String> _statusLabels = {
    BudgetStatus.safe: 'On track',
    BudgetStatus.warning: 'Nearing limit',
    BudgetStatus.over: 'Over budget',
  };

  @override
  Widget build(BuildContext context) {
    final color = _colors[progress.status]!;
    final title = label ?? (progress.budget.isOverall ? 'Overall' : progress.budget.categoryId!);

    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(_icons[progress.status], color: color, size: 20),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(title, style: Theme.of(context).textTheme.titleMedium),
                  ),
                  Text(
                    _statusLabels[progress.status]!,
                    style: Theme.of(context).textTheme.labelMedium?.copyWith(color: color),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: LinearProgressIndicator(
                  value: progress.ratio.clamp(0.0, 1.0),
                  minHeight: 8,
                  backgroundColor: color.withValues(alpha: 0.15),
                  color: color,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '${progress.spent.formatted} of ${progress.budget.limit.formatted}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
