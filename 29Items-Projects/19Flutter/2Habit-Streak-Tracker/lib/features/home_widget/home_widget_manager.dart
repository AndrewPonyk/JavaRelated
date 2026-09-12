import '../../core/services/home_widget_service.dart';
import '../../core/utils/date_time_utils.dart';
import '../habits/domain/models/habit.dart';
import 'home_widget_payload.dart';

/// Coordinator formatting and sending updates to home widgets
class HomeWidgetManager {
  final HomeWidgetService widgetService;

  HomeWidgetManager({required this.widgetService});

  Future<void> syncHabitsToWidget(List<Habit> activeHabits) async {
    final todayWeekday = DateTime.now().weekday;
    // Filter to habits scheduled for today
    final todaysHabits = activeHabits
        .where((h) => h.frequency.isScheduledForWeekday(todayWeekday))
        .toList();

    final completedCount = todaysHabits.where((h) => h.isCompletedToday).length;

    final payload = HomeWidgetPayload(
      lastUpdated: DateTimeUtils.toDateString(DateTime.now()),
      completedCount: completedCount,
      totalCount: todaysHabits.length,
      habits: todaysHabits
          .map((h) => HomeWidgetHabitItem.fromHabit(h))
          .toList(),
    );

    await widgetService.updateWidgetData(payload.toJson());
  }
}
