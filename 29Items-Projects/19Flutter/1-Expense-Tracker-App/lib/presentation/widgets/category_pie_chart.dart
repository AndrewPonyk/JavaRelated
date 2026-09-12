/// Category breakdown pie chart, with a text legend so the colour-blind-safe
/// palette (`AppColors.categoryPalette`) is never the only way to read a
/// slice (docs/TECH-NOTES.md §3.6).
library;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../data/models/expense_category.dart';
import '../../domain/services/analytics_service.dart';

class CategoryPieChart extends StatelessWidget {
  const CategoryPieChart({
    super.key,
    required this.totals,
    required this.categories,
  });

  final List<CategoryTotal> totals;
  final Map<String, ExpenseCategory> categories;

  @override
  Widget build(BuildContext context) {
    if (totals.isEmpty) {
      return const SizedBox(
        height: 160,
        child: Center(child: Text('No spending yet this period.')),
      );
    }

    final grandTotal = totals.fold<double>(0, (sum, t) => sum + t.total.asDouble);

    return Column(
      children: [
        SizedBox(
          height: 200,
          child: PieChart(
            PieChartData(
              sectionsSpace: 2,
              centerSpaceRadius: 40,
              sections: [
                for (var i = 0; i < totals.length; i++)
                  PieChartSectionData(
                    value: totals[i].total.asDouble,
                    color: _colorFor(totals[i].categoryId, i),
                    title: grandTotal == 0
                        ? ''
                        : '${(totals[i].total.asDouble / grandTotal * 100).round()}%',
                    radius: 56,
                    titleStyle: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        for (var i = 0; i < totals.length; i++)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: _colorFor(totals[i].categoryId, i),
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(categories[totals[i].categoryId]?.name ?? totals[i].categoryId),
                ),
                Text(totals[i].total.formatted),
              ],
            ),
          ),
      ],
    );
  }

  Color _colorFor(String categoryId, int index) {
    final category = categories[categoryId];
    return category != null ? Color(category.colorValue) : AppColors.categoryPalette[index % AppColors.categoryPalette.length];
  }
}
