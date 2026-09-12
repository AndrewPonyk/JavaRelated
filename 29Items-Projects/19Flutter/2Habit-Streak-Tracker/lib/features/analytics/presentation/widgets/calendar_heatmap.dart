import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/utils/date_time_utils.dart';

/// GitHub-style calendar contribution heatmap visualizing daily habit completions
class CalendarHeatmap extends StatelessWidget {
  final Map<String, int> dailyCounts;
  final int daysToShow;
  final void Function(String dateString, int count)? onDayTap;

  const CalendarHeatmap({
    super.key,
    required this.dailyCounts,
    this.daysToShow = 84, // 12 weeks of 7 days
    this.onDayTap,
  });

  Color _getColorForCount(int count) {
    if (count == 0) return AppColors.heatmapShadesDark[0];
    if (count == 1) return AppColors.heatmapShadesDark[1];
    if (count == 2) return AppColors.heatmapShadesDark[2];
    if (count <= 4) return AppColors.heatmapShadesDark[3];
    return AppColors.heatmapShadesDark[4];
  }

  @override
  Widget build(BuildContext context) {
    final today = DateTimeUtils.today();
    final List<DateTime> days = [];

    // Calculate dates backwards to create grid of weeks
    for (int i = daysToShow - 1; i >= 0; i--) {
      days.add(today.subtract(Duration(days: i)));
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceDark,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.borderDark, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Activity Heatmap',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimaryDark,
            ),
          ),
          const SizedBox(height: 12),
          // Scrollable horizontal grid of weeks
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            reverse: true,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: List.generate((days.length / 7).ceil(), (weekIndex) {
                return Padding(
                  padding: const EdgeInsets.only(right: 4),
                  child: Column(
                    children: List.generate(7, (dayIndex) {
                      final itemIndex = (weekIndex * 7) + dayIndex;
                      if (itemIndex >= days.length) {
                        return const SizedBox(width: 14, height: 14);
                      }

                      final date = days[itemIndex];
                      final dateStr = DateTimeUtils.toDateString(date);
                      final count = dailyCounts[dateStr] ?? 0;
                      final cellColor = _getColorForCount(count);

                      return GestureDetector(
                        onTap: () => onDayTap?.call(dateStr, count),
                        child: Container(
                          width: 14,
                          height: 14,
                          margin: const EdgeInsets.only(bottom: 4),
                          decoration: BoxDecoration(
                            color: cellColor,
                            borderRadius: BorderRadius.circular(3),
                          ),
                        ),
                      );
                    }),
                  ),
                );
              }),
            ),
          ),
          const SizedBox(height: 8),
          // Legend
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              const Text(
                'Less',
                style: TextStyle(
                  fontSize: 10,
                  color: AppColors.textSecondaryDark,
                ),
              ),
              const SizedBox(width: 4),
              for (final color in AppColors.heatmapShadesDark) ...[
                Container(
                  width: 10,
                  height: 10,
                  margin: const EdgeInsets.symmetric(horizontal: 2),
                  decoration: BoxDecoration(
                    color: color,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ],
              const SizedBox(width: 4),
              const Text(
                'More',
                style: TextStyle(
                  fontSize: 10,
                  color: AppColors.textSecondaryDark,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
