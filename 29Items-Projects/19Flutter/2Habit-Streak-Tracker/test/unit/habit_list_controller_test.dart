import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/core/errors/failures.dart';
import 'package:habit_streak_tracker/core/services/home_widget_service.dart';
import 'package:habit_streak_tracker/core/services/notification_service.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit.dart';
import 'package:habit_streak_tracker/features/habits/domain/models/habit_frequency.dart';
import 'package:habit_streak_tracker/features/habits/domain/repositories/habit_repository.dart';
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

  group('HabitListController Unit Tests', () {
    late MockHabitRepository mockRepo;
    late MockNotificationService mockNotif;
    late MockHomeWidgetService mockHomeWidget;

    final initialHabit = Habit(
      id: 'h-1',
      title: 'Workout',
      frequency: const HabitFrequency(type: FrequencyType.daily),
      createdAt: DateTime(2026, 9, 1),
      currentStreak: 2,
      isCompletedToday: false,
    );

    setUp(() {
      mockRepo = MockHabitRepository();
      mockNotif = MockNotificationService();
      mockHomeWidget = MockHomeWidgetService();

      when(
        () => mockRepo.getActiveHabits(),
      ).thenAnswer((_) async => [initialHabit]);
      when(() => mockRepo.createHabit(any())).thenAnswer((_) async {});
      when(() => mockRepo.archiveHabit(any())).thenAnswer((_) async {});
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

    test('Initial build loads active habits', () async {
      final container = ProviderContainer(
        overrides: [
          habitRepositoryProvider.overrideWithValue(mockRepo),
          notificationServiceProvider.overrideWithValue(mockNotif),
          homeWidgetServiceProvider.overrideWithValue(mockHomeWidget),
        ],
      );
      addTearDown(container.dispose);

      final habits = await container.read(habitListControllerProvider.future);
      expect(habits.length, equals(1));
      expect(habits.first.title, equals('Workout'));
    });

    test(
      'toggleHabitCompletion rolls back to previous state on repository failure',
      () async {
        when(
          () => mockRepo.toggleCompletion(
            habitId: any(named: 'habitId'),
            dateString: any(named: 'dateString'),
          ),
        ).thenThrow(const DatabaseFailure('Disk I/O Error'));

        final container = ProviderContainer(
          overrides: [
            habitRepositoryProvider.overrideWithValue(mockRepo),
            notificationServiceProvider.overrideWithValue(mockNotif),
            homeWidgetServiceProvider.overrideWithValue(mockHomeWidget),
          ],
        );
        addTearDown(container.dispose);

        await container.read(habitListControllerProvider.future);

        await container
            .read(habitListControllerProvider.notifier)
            .toggleHabitCompletion('h-1');

        final state = container.read(habitListControllerProvider);
        expect(state.hasError, isTrue);
        expect(state.error, isA<DatabaseFailure>());
      },
    );

    test(
      'addHabit creates habit in repository and schedules reminder if configured',
      () async {
        final container = ProviderContainer(
          overrides: [
            habitRepositoryProvider.overrideWithValue(mockRepo),
            notificationServiceProvider.overrideWithValue(mockNotif),
            homeWidgetServiceProvider.overrideWithValue(mockHomeWidget),
          ],
        );
        addTearDown(container.dispose);

        final newHabit = Habit(
          id: 'h-new',
          title: 'Read Books',
          frequency: const HabitFrequency(type: FrequencyType.daily),
          createdAt: DateTime.now(),
          reminderTime: '21:00',
        );

        when(
          () => mockRepo.getActiveHabits(),
        ).thenAnswer((_) async => [initialHabit, newHabit]);

        await container
            .read(habitListControllerProvider.notifier)
            .addHabit(newHabit);

        verify(() => mockRepo.createHabit(newHabit)).called(1);
        verify(
          () => mockNotif.scheduleDailyHabitReminder(
            habitNotificationId: newHabit.id.hashCode,
            habitTitle: 'Read Books',
            reminderTime: '21:00',
          ),
        ).called(1);
      },
    );

    test(
      'archiveHabit archives in repository and cancels notification',
      () async {
        final container = ProviderContainer(
          overrides: [
            habitRepositoryProvider.overrideWithValue(mockRepo),
            notificationServiceProvider.overrideWithValue(mockNotif),
            homeWidgetServiceProvider.overrideWithValue(mockHomeWidget),
          ],
        );
        addTearDown(container.dispose);

        when(() => mockRepo.getActiveHabits()).thenAnswer((_) async => []);

        await container
            .read(habitListControllerProvider.notifier)
            .archiveHabit('h-1');

        verify(() => mockRepo.archiveHabit('h-1')).called(1);
        verify(() => mockNotif.cancelReminder('h-1'.hashCode)).called(1);
      },
    );

    test('applyStreakFreeze delegates to repository', () async {
      final container = ProviderContainer(
        overrides: [
          habitRepositoryProvider.overrideWithValue(mockRepo),
          notificationServiceProvider.overrideWithValue(mockNotif),
          homeWidgetServiceProvider.overrideWithValue(mockHomeWidget),
        ],
      );
      addTearDown(container.dispose);

      await container
          .read(habitListControllerProvider.notifier)
          .applyStreakFreeze('h-1', '2026-09-05', reason: 'Sick');

      verify(
        () => mockRepo.applyStreakFreeze(
          habitId: 'h-1',
          dateString: '2026-09-05',
          reason: 'Sick',
        ),
      ).called(1);
    });
  });
}
