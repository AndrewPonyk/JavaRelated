import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';

/// Compact badge displaying active streak count with flame icon
class StreakBadge extends StatelessWidget {
  final int streak;
  final bool hasActiveFreeze;

  const StreakBadge({
    super.key,
    required this.streak,
    this.hasActiveFreeze = false,
  });

  @override
  Widget build(BuildContext context) {
    final bool isZero = streak == 0;
    final Color flameColor = hasActiveFreeze
        ? AppColors.streakFreeze
        : (isZero ? AppColors.textSecondaryDark : AppColors.streakFlame);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: flameColor.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: flameColor.withValues(alpha: 0.3), width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            hasActiveFreeze
                ? Icons.ac_unit
                : Icons.local_fire_department_rounded,
            size: 16,
            color: flameColor,
          ),
          const SizedBox(width: 4),
          Text(
            '$streak',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: flameColor,
            ),
          ),
        ],
      ),
    );
  }
}
