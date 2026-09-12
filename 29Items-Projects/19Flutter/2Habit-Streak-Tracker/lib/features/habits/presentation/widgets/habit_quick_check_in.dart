import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';

/// One-tap interactive check-in button with fluid animation
class HabitQuickCheckIn extends StatelessWidget {
  final bool isCompleted;
  final VoidCallback onToggle;
  final Color activeColor;

  const HabitQuickCheckIn({
    super.key,
    required this.isCompleted,
    required this.onToggle,
    this.activeColor = AppColors.accent,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onToggle,
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeInOut,
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: isCompleted ? activeColor : Colors.transparent,
          shape: BoxShape.circle,
          border: Border.all(
            color: isCompleted ? activeColor : AppColors.borderDark,
            width: 2,
          ),
          boxShadow: isCompleted
              ? [
                  BoxShadow(
                    color: activeColor.withValues(alpha: 0.4),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ]
              : [],
        ),
        child: Center(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            child: isCompleted
                ? const Icon(
                    Icons.check_rounded,
                    key: ValueKey('completed'),
                    color: Colors.white,
                    size: 24,
                  )
                : const SizedBox.shrink(key: ValueKey('uncompleted')),
          ),
        ),
      ),
    );
  }
}
