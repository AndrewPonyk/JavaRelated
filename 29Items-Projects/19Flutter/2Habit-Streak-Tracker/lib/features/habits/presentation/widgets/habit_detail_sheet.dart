import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/utils/date_time_utils.dart';
import '../../domain/models/habit.dart';
import '../controllers/habit_detail_controller.dart';

/// Bottom modal sheet allowing users to view habit history, apply streak freeze, and edit or delete habit
class HabitDetailSheet extends ConsumerStatefulWidget {
  final Habit habit;

  const HabitDetailSheet({super.key, required this.habit});

  @override
  ConsumerState<HabitDetailSheet> createState() => _HabitDetailSheetState();
}

class _HabitDetailSheetState extends ConsumerState<HabitDetailSheet> {
  late TextEditingController _titleController;
  late TextEditingController _descController;
  String? _selectedReminderTime;

  @override
  void initState() {
    super.initState();
    _titleController = TextEditingController(text: widget.habit.title);
    _descController = TextEditingController(
      text: widget.habit.description ?? '',
    );
    _selectedReminderTime = widget.habit.reminderTime;
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _pickReminderTime() async {
    final initial =
        DateTimeUtils.parseTimeString(_selectedReminderTime) ?? (8, 0);
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: initial.$1, minute: initial.$2),
    );

    if (picked != null) {
      final hourStr = picked.hour.toString().padLeft(2, '0');
      final minStr = picked.minute.toString().padLeft(2, '0');
      setState(() {
        _selectedReminderTime = '$hourStr:$minStr';
      });
    }
  }

  void _applyFreezeYesterday() {
    final yesterday = DateTime.now().subtract(const Duration(days: 1));
    final yesterdayStr = DateTimeUtils.toDateString(yesterday);

    ref
        .read(habitDetailControllerProvider(widget.habit.id).notifier)
        .applyFreeze(yesterdayStr, reason: 'Rest day / Sickness');

    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Streak freeze activated for $yesterdayStr! ❄️'),
        backgroundColor: AppColors.streakFreeze,
      ),
    );
  }

  void _saveChanges() {
    final title = _titleController.text.trim();
    if (title.isEmpty) return;

    final updated = widget.habit.copyWith(
      title: title,
      description: _descController.text.trim().isEmpty
          ? null
          : _descController.text.trim(),
      reminderTime: _selectedReminderTime,
    );

    ref
        .read(habitDetailControllerProvider(widget.habit.id).notifier)
        .updateHabit(updated);

    Navigator.pop(context);
  }

  void _confirmDelete() {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surfaceDark,
        title: const Text(
          'Delete Habit?',
          style: TextStyle(color: AppColors.textPrimaryDark),
        ),
        content: const Text(
          'Are you sure? This will delete all historic completion data and streak statistics for this habit.',
          style: TextStyle(color: AppColors.textSecondaryDark),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.redAccent),
            onPressed: () {
              Navigator.pop(ctx);
              ref
                  .read(habitDetailControllerProvider(widget.habit.id).notifier)
                  .deleteHabit();
              Navigator.pop(context);
            },
            child: const Text('Delete'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.only(
        top: 24,
        left: 20,
        right: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surfaceDark,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Habit Details & Settings',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimaryDark,
                  ),
                ),
                IconButton(
                  icon: const Icon(
                    Icons.delete_outline_rounded,
                    color: Colors.redAccent,
                  ),
                  onPressed: _confirmDelete,
                  tooltip: 'Delete Habit',
                ),
              ],
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _titleController,
              style: const TextStyle(color: AppColors.textPrimaryDark),
              decoration: InputDecoration(
                labelText: 'Habit Name',
                labelStyle: const TextStyle(color: AppColors.textSecondaryDark),
                filled: true,
                fillColor: AppColors.cardDark,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _descController,
              style: const TextStyle(color: AppColors.textPrimaryDark),
              decoration: InputDecoration(
                labelText: 'Notes / Motivation (Optional)',
                labelStyle: const TextStyle(color: AppColors.textSecondaryDark),
                filled: true,
                fillColor: AppColors.cardDark,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 16),
            // Reminder Row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Row(
                  children: [
                    Icon(
                      Icons.alarm,
                      color: AppColors.textSecondaryDark,
                      size: 20,
                    ),
                    SizedBox(width: 8),
                    Text(
                      'Daily Reminder:',
                      style: TextStyle(
                        color: AppColors.textPrimaryDark,
                        fontSize: 14,
                      ),
                    ),
                  ],
                ),
                TextButton(
                  onPressed: _pickReminderTime,
                  child: Text(
                    _selectedReminderTime ?? 'Set Reminder',
                    style: const TextStyle(
                      color: AppColors.primary,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const Divider(color: AppColors.borderDark),
            const SizedBox(height: 8),
            // Streak Freeze Action
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.streakFreeze.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: AppColors.streakFreeze.withValues(alpha: 0.3),
                ),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.ac_unit,
                    color: AppColors.streakFreeze,
                    size: 24,
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Missed a day? Freeze Streak',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: AppColors.textPrimaryDark,
                          ),
                        ),
                        Text(
                          'Protects your current streak for sick or travel days.',
                          style: TextStyle(
                            fontSize: 11,
                            color: AppColors.textSecondaryDark,
                          ),
                        ),
                      ],
                    ),
                  ),
                  OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.streakFreeze,
                      side: const BorderSide(color: AppColors.streakFreeze),
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                    ),
                    onPressed: _applyFreezeYesterday,
                    child: const Text('Freeze'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _saveChanges,
                child: const Text('Save Changes'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
