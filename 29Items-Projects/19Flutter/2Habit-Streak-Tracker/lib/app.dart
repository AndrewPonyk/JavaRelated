import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'core/utils/date_time_utils.dart';
import 'features/analytics/presentation/controllers/stats_controller.dart';
import 'features/analytics/presentation/widgets/calendar_heatmap.dart';
import 'features/analytics/presentation/widgets/stats_summary_card.dart';
import 'features/habits/domain/models/habit.dart';
import 'features/habits/domain/models/habit_frequency.dart';
import 'features/habits/presentation/providers/habit_providers.dart';
import 'features/habits/presentation/widgets/habit_card.dart';
import 'features/habits/presentation/widgets/habit_detail_sheet.dart';

class HabitTrackerApp extends StatelessWidget {
  const HabitTrackerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Habit Streak Tracker',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.dark,
      home: const HabitHomeScreen(),
    );
  }
}

class HabitHomeScreen extends ConsumerWidget {
  const HabitHomeScreen({super.key});

  void _showAddHabitSheet(BuildContext context, WidgetRef ref) {
    final titleController = TextEditingController();
    FrequencyType selectedFrequency = FrequencyType.daily;
    int selectedColor = 0xFF6366F1;
    String? selectedReminderTime;

    final colorOptions = [
      0xFF6366F1, // Indigo
      0xFF10B981, // Emerald
      0xFFF97316, // Orange
      0xFFEC4899, // Pink
      0xFF06B6D4, // Cyan
      0xFF8B5CF6, // Purple
    ];

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surfaceDark,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (bottomSheetContext) {
        return StatefulBuilder(
          builder: (context, setState) {
            return Padding(
              padding: EdgeInsets.only(
                bottom: MediaQuery.of(context).viewInsets.bottom + 24,
                top: 24,
                left: 20,
                right: 20,
              ),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Create New Habit',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: AppColors.textPrimaryDark,
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: titleController,
                      autofocus: true,
                      style: const TextStyle(color: AppColors.textPrimaryDark),
                      decoration: InputDecoration(
                        hintText: 'e.g., Drink 2L Water, Read 20 mins',
                        hintStyle: const TextStyle(
                          color: AppColors.textSecondaryDark,
                        ),
                        filled: true,
                        fillColor: AppColors.cardDark,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Frequency',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textSecondaryDark,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      children: FrequencyType.values.map((freq) {
                        final isSelected = freq == selectedFrequency;
                        return ChoiceChip(
                          label: Text(freq.name),
                          selected: isSelected,
                          onSelected: (selected) {
                            if (selected) {
                              setState(() => selectedFrequency = freq);
                            }
                          },
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 16),
                    // Color selection
                    const Text(
                      'Habit Color',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textSecondaryDark,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: colorOptions.map((c) {
                        final isSelected = c == selectedColor;
                        return GestureDetector(
                          onTap: () => setState(() => selectedColor = c),
                          child: Container(
                            margin: const EdgeInsets.only(right: 12),
                            width: 32,
                            height: 32,
                            decoration: BoxDecoration(
                              color: Color(c),
                              shape: BoxShape.circle,
                              border: isSelected
                                  ? Border.all(color: Colors.white, width: 3)
                                  : null,
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 16),
                    // Reminder Picker
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          'Daily Reminder:',
                          style: TextStyle(
                            color: AppColors.textSecondaryDark,
                            fontSize: 14,
                          ),
                        ),
                        TextButton.icon(
                          icon: const Icon(Icons.alarm, size: 18),
                          label: Text(selectedReminderTime ?? 'Disabled'),
                          onPressed: () async {
                            final picked = await showTimePicker(
                              context: context,
                              initialTime: const TimeOfDay(hour: 8, minute: 0),
                            );
                            if (picked != null) {
                              final h = picked.hour.toString().padLeft(2, '0');
                              final m = picked.minute.toString().padLeft(
                                2,
                                '0',
                              );
                              setState(() => selectedReminderTime = '$h:$m');
                            }
                          },
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
                        onPressed: () async {
                          final text = titleController.text.trim();
                          if (text.isEmpty) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text('Please enter a habit title'),
                                backgroundColor: Colors.redAccent,
                              ),
                            );
                            return;
                          }

                          final newHabit = Habit(
                            id: const Uuid().v4(),
                            title: text,
                            frequency: HabitFrequency(type: selectedFrequency),
                            colorValue: selectedColor,
                            reminderTime: selectedReminderTime,
                            createdAt: DateTime.now(),
                          );

                          try {
                            await ref
                                .read(habitListControllerProvider.notifier)
                                .addHabit(newHabit);
                            ref.invalidate(statsControllerProvider);

                            if (bottomSheetContext.mounted) {
                              Navigator.pop(bottomSheetContext);
                            }

                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text('Habit "$text" created!'),
                                  backgroundColor: AppColors.accent,
                                ),
                              );
                            }
                          } catch (e) {
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text('Error adding habit: $e'),
                                  backgroundColor: Colors.redAccent,
                                ),
                              );
                            }
                          }
                        },
                        child: const Text('Add Habit'),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  void _showHabitDetails(BuildContext context, Habit habit) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => HabitDetailSheet(habit: habit),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final habitListAsync = ref.watch(habitListControllerProvider);
    final statsAsync = ref.watch(statsControllerProvider);

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Habit Streaks'),
            Text(
              DateTimeUtils.todayString(),
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textSecondaryDark,
                fontWeight: FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(habitListControllerProvider);
          ref.invalidate(statsControllerProvider);
        },
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          children: [
            // Statistics Summary Card
            statsAsync.when(
              data: (stats) => StatsSummaryCard(stats: stats),
              loading: () => const SizedBox(
                height: 70,
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (_, __) => const SizedBox.shrink(),
            ),

            const SizedBox(height: 14),

            // Calendar Heatmap Section
            statsAsync.when(
              data: (stats) =>
                  CalendarHeatmap(dailyCounts: stats.dailyHeatmapCounts),
              loading: () => const SizedBox.shrink(),
              error: (_, __) => const SizedBox.shrink(),
            ),

            const SizedBox(height: 20),

            const Text(
              "Today's Routine",
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimaryDark,
              ),
            ),
            const SizedBox(height: 10),

            // Habit List
            habitListAsync.when(
              data: (habits) {
                if (habits.isEmpty) {
                  return Container(
                    padding: const EdgeInsets.symmetric(vertical: 48),
                    alignment: Alignment.center,
                    child: Column(
                      children: [
                        Icon(
                          Icons.track_changes_rounded,
                          size: 48,
                          color: AppColors.textSecondaryDark.withValues(
                            alpha: 0.5,
                          ),
                        ),
                        const SizedBox(height: 12),
                        const Text(
                          'No habits yet. Start your first streak!',
                          style: TextStyle(
                            color: AppColors.textSecondaryDark,
                            fontSize: 14,
                          ),
                        ),
                      ],
                    ),
                  );
                }

                return Column(
                  children: habits.map((habit) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: HabitCard(
                        habit: habit,
                        onToggle: () {
                          ref
                              .read(habitListControllerProvider.notifier)
                              .toggleHabitCompletion(habit.id);
                          ref.invalidate(statsControllerProvider);
                        },
                        onTap: () => _showHabitDetails(context, habit),
                      ),
                    );
                  }).toList(),
                );
              },
              loading: () => const Center(
                child: Padding(
                  padding: EdgeInsets.all(32.0),
                  child: CircularProgressIndicator(),
                ),
              ),
              error: (err, stack) => Center(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      Text(
                        'Failed to load habits:\n$err',
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Colors.redAccent),
                      ),
                      const SizedBox(height: 12),
                      ElevatedButton(
                        onPressed: () {
                          ref.invalidate(habitListControllerProvider);
                          ref.invalidate(statsControllerProvider);
                        },
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showAddHabitSheet(context, ref),
        backgroundColor: AppColors.primary,
        icon: const Icon(Icons.add_rounded, color: Colors.white),
        label: const Text(
          'New Habit',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
      ),
    );
  }
}
