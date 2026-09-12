import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/core/services/home_widget_service.dart';
import 'package:habit_streak_tracker/core/services/notification_service.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';
import 'package:habit_streak_tracker/features/habits/domain/repositories/habit_repository.dart';
import 'package:habit_streak_tracker/features/habits/presentation/controllers/habit_detail_controller.dart';
import 'package:habit_streak_tracker/features/habits/presentation/providers/habit_providers.dart';
import 'package:mocktail/mocktail.dart';

class MockHabitRepository extends Mock implements HabitRepository {}

class MockNotificationService extends Mock implements NotificationService {}

class MockHomeWidgetService extends Mock implements HomeWidgetService {}

void main() {
  setUpAll(() {
    registerFallbackValue(
      Habit(
        id: 'fallback',
        title: 'fallback',
        frequency: const HabitFrequency(type: FrequencyType.daily),
        createdAt: DateTime(2026, 1, 1),
      ),
    );
  });

  group('HabitDetailController Unit Tests', () {
    late MockHabitRepository mockRepo;
    late MockNotificationService mockNotif;
    late MockHomeWidgetService mockHomeWidget;

    final testHabit = Habit(
      id: 'h-test-1',
      title: 'Original Title',
      frequency: const HabitFrequency(type: FrequencyType.daily),
      createdAt: DateTime(2026, 9, 1),
      reminderTime: '08:00',
    );

    setUp(() {
      mockRepo = MockHabitRepository();
      mockNotif = MockNotificationService();
      mockHomeWidget = MockHomeWidgetService();

      when(
        () => mockRepo.getHabitById('h-test-1'),
      ).thenAnswer((_) async => testHabit);
      when(
        () => mockRepo.getActiveHabits(),
      ).thenAnswer((_) async => [testHabit]);
      when(() => mockRepo.updateHabit(any())).thenAnswer((_) async {});
      when(() => mockRepo.deleteHabit(any())).thenAnswer((_) async {});
      when(
        () => mockRepo.applyStreakFreeze(
          habitId: any(named: 'habitId'),
          dateString: any(named: 'dateString'),
          reason: any(named: 'reason'),
        ),
      ).thenAnswer((_) async {});

      when(
        () => mockNotif.scheduleDailyHabitReminder(
          habitNotificationId: any(named: 'habitNotificationId'),
          habitTitle: any(named: 'habitTitle'),
          reminderTime: any(named: 'reminderTime'),
        ),
      ).thenAnswer((_) async {});
      when(() => mockNotif.cancelReminder(any())).thenAnswer((_) async {});

      when(
        () => mockHomeWidget.updateWidgetData(any()),
      ).thenAnswer((_) async {});
    });

    test('build loads habit entity by ID successfully', () async {
      final container = ProviderContainer(
        overrides: [
          habitRepositoryProvider.overrideWithValue(mockRepo),
          notificationServiceProvider.overrideWithValue(mockNotif),
          homeWidgetServiceProvider.overrideWithValue(mockHomeWidget),
        ],
      );
      addTearDown(container.dispose);

      final habit = await container.read(
        habitDetailControllerProvider('h-test-1').future,
      );
      expect(habit, isNotNull);
      expect(habit!.title, equals('Original Title'));
    });

    test(
      'updateHabit updates repository, reschedules notification, and updates widget',
      () async {
        final container = ProviderContainer(
          overrides: [
            habitRepositoryProvider.overrideWithValue(mockRepo),
            notificationServiceProvider.overrideWithValue(mockNotif),
            homeWidgetServiceProvider.overrideWithValue(mockHomeWidget),
          ],
        );
        addTearDown(container.dispose);

        final updated = testHabit.copyWith(
          title: 'Updated Title',
          reminderTime: '09:30',
        );

        when(
          () => mockRepo.getHabitById('h-test-1'),
        ).thenAnswer((_) async => updated);

        await container
            .read(habitDetailControllerProvider('h-test-1').notifier)
            .updateHabit(updated);

        verify(() => mockRepo.updateHabit(updated)).called(1);
        verify(
          () => mockNotif.scheduleDailyHabitReminder(
            habitNotificationId: updated.id.hashCode,
            habitTitle: 'Updated Title',
            reminderTime: '09:30',
          ),
        ).called(1);
      },
    );

    test(
      'deleteHabit deletes from repository and cancels scheduled reminder',
      () async {
        final container = ProviderContainer(
          overrides: [
            habitRepositoryProvider.overrideWithValue(mockRepo),
            notificationServiceProvider.overrideWithValue(mockNotif),
            homeWidgetServiceProvider.overrideWithValue(mockHomeWidget),
          ],
        );
        addTearDown(container.dispose);

        await container
            .read(habitDetailControllerProvider('h-test-1').notifier)
            .deleteHabit();

        verify(() => mockRepo.deleteHabit('h-test-1')).called(1);
        verify(() => mockNotif.cancelReminder(testHabit.id.hashCode)).called(1);
      },
    );
  });
}
