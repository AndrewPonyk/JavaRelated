import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/models/habit.dart';
import '../../domain/models/habit_frequency.dart';
import 'habit_quick_check_in.dart';
import 'streak_badge.dart';

/// Primary list card widget for displaying a habit with quick completion action
class HabitCard extends StatelessWidget {
  final Habit habit;
  final VoidCallback onToggle;
  final VoidCallback? onTap;

  const HabitCard({
    super.key,
    required this.habit,
    required this.onToggle,
    this.onTap,
  });

  String _formatFrequency(HabitFrequency freq) {
    switch (freq.type) {
      case FrequencyType.daily:
        return 'Every day';
      case FrequencyType.weekdaysOnly:
        return 'Weekdays';
      case FrequencyType.weekendsOnly:
        return 'Weekends';
      case FrequencyType.specificDays:
        const dayNames = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
        return freq.specificDays.map((d) => dayNames[d]).join(', ');
    }
  }

  @override
  Widget build(BuildContext context) {
    final habitColor = Color(habit.colorValue);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.surfaceDark,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: habit.isCompletedToday
                ? habitColor.withValues(alpha: 0.4)
                : AppColors.borderDark,
            width: 1,
          ),
        ),
        child: Row(
          children: [
            // Left color accent bar
            Container(
              width: 4,
              height: 48,
              decoration: BoxDecoration(
                color: habitColor,
                borderRadius: BorderRadius.circular(4),
              ),
            ),
            const SizedBox(width: 14),

            // Title & Frequency info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    habit.title,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: habit.isCompletedToday
                          ? AppColors.textSecondaryDark
                          : AppColors.textPrimaryDark,
                      decoration: habit.isCompletedToday
                          ? TextDecoration.lineThrough
                          : null,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(
                        Icons.repeat_rounded,
                        size: 13,
                        color: AppColors.textSecondaryDark,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        _formatFrequency(habit.frequency),
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondaryDark,
                        ),
                      ),
                      if (habit.reminderTime != null) ...[
                        const SizedBox(width: 10),
                        const Icon(
                          Icons.notifications_none_rounded,
                          size: 13,
                          color: AppColors.textSecondaryDark,
                        ),
                        const SizedBox(width: 3),
                        Text(
                          habit.reminderTime!,
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondaryDark,
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(width: 12),

            // Streak Badge
            StreakBadge(streak: habit.currentStreak),

            const SizedBox(width: 12),

            // Interactive Check-In
            HabitQuickCheckIn(
              isCompleted: habit.isCompletedToday,
              onToggle: onToggle,
              activeColor: habitColor,
            ),
          ],
        ),
      ),
    );
  }
}
