import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/database/app_database.dart';
import '../../../../core/services/home_widget_service.dart';
import '../../../../core/services/notification_service.dart';
import '../../data/datasources/habit_completion_local_datasource.dart';
import '../../data/datasources/habit_local_datasource.dart';
import '../../data/repositories/habit_repository_impl.dart';
import '../../domain/models/habit.dart';
import '../../domain/repositories/habit_repository.dart';
import '../../../home_widget/home_widget_manager.dart';
import '../controllers/habit_list_controller.dart';

// Database & Core Services Providers
final appDatabaseProvider = Provider<AppDatabase>((ref) {
  return AppDatabase.instance;
});

final notificationServiceProvider = Provider<NotificationService>((ref) {
  return NotificationService.instance;
});

final homeWidgetServiceProvider = Provider<HomeWidgetService>((ref) {
  return HomeWidgetService.instance;
});

final homeWidgetManagerProvider = Provider<HomeWidgetManager>((ref) {
  final service = ref.watch(homeWidgetServiceProvider);
  return HomeWidgetManager(widgetService: service);
});

// DataSources Providers
final habitLocalDatasourceProvider = Provider<HabitLocalDatasource>((ref) {
  final db = ref.watch(appDatabaseProvider);
  return HabitLocalDatasource(appDatabase: db);
});

final habitCompletionLocalDatasourceProvider =
    Provider<HabitCompletionLocalDatasource>((ref) {
      final db = ref.watch(appDatabaseProvider);
      return HabitCompletionLocalDatasource(appDatabase: db);
    });

// Repository Provider
final habitRepositoryProvider = Provider<HabitRepository>((ref) {
  final habitDs = ref.watch(habitLocalDatasourceProvider);
  final completionDs = ref.watch(habitCompletionLocalDatasourceProvider);
  return HabitRepositoryImpl(
    habitDatasource: habitDs,
    completionDatasource: completionDs,
  );
});

// Presentation Controller Provider
final habitListControllerProvider =
    AsyncNotifierProvider<HabitListController, List<Habit>>(() {
      return HabitListController();
    });
