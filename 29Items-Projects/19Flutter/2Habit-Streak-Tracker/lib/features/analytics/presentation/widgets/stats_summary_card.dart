import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/habit_stats.dart';

/// Overview summary card showing key streak metrics and completion statistics
class StatsSummaryCard extends StatelessWidget {
  final HabitStats stats;

  const StatsSummaryCard({super.key, required this.stats});

  Widget _buildMetricTile({
    required IconData icon,
    required Color iconColor,
    required String value,
    required String label,
  }) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: iconColor.withValues(alpha: 0.15),
            shape: BoxShape.circle,
          ),
          child: Icon(icon, color: iconColor, size: 20),
        ),
        const SizedBox(height: 6),
        Text(
          value,
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimaryDark,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          style: const TextStyle(
            fontSize: 11,
            color: AppColors.textSecondaryDark,
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: AppColors.surfaceDark,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.borderDark, width: 0.5),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _buildMetricTile(
            icon: Icons.local_fire_department_rounded,
            iconColor: AppColors.streakFlame,
            value: '${stats.activeStreakMax}d',
            label: 'Best Streak',
          ),
          Container(width: 1, height: 40, color: AppColors.borderDark),
          _buildMetricTile(
            icon: Icons.check_circle_outline_rounded,
            iconColor: AppColors.accent,
            value: '${stats.totalCompletions}',
            label: 'Check-ins',
          ),
          Container(width: 1, height: 40, color: AppColors.borderDark),
          _buildMetricTile(
            icon: Icons.pie_chart_outline_rounded,
            iconColor: AppColors.primary,
            value: '${stats.averageCompletionRate.toStringAsFixed(0)}%',
            label: '30d Rate',
          ),
          Container(width: 1, height: 40, color: AppColors.borderDark),
          _buildMetricTile(
            icon: Icons.repeat_rounded,
            iconColor: AppColors.streakFreeze,
            value: '${stats.totalHabits}',
            label: 'Habits',
          ),
        ],
      ),
    );
  }
}
