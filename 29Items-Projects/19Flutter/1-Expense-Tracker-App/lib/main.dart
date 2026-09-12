/// Composition root. Wires storage, migrations, repositories, services, and
/// providers once, then hands everything to [ExpenseTrackerApp]. Nothing
/// below `main()` constructs its own dependencies (ARCHITECTURE §2.2) — this
/// is the only place `new`s a repository or service.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'app.dart';
import 'core/config/app_config.dart';
import 'core/error/error_handler.dart';
import 'core/logging/app_logger.dart';
import 'core/constants/hive_boxes.dart';
import 'core/utils/result.dart';
import 'data/datasources/hive_service.dart';
import 'data/datasources/notification_gateway.dart';
import 'data/migrations/hive_migrator.dart';
import 'data/repositories/budget_repository.dart';
import 'data/repositories/currency_repository.dart';
import 'data/repositories/expense_repository.dart';
import 'data/repositories/recurring_repository.dart';
import 'data/repositories/settings_repository.dart';
import 'data/repositories/template_repository.dart';
import 'domain/services/analytics_service.dart';
import 'domain/services/budget_service.dart';
import 'domain/services/csv_export_service.dart';
import 'domain/services/currency_service.dart';
import 'domain/services/notification_service.dart';
import 'domain/services/recurring_service.dart';
import 'presentation/providers/analytics_provider.dart';
import 'presentation/providers/budget_provider.dart';
import 'presentation/providers/expense_provider.dart';
import 'presentation/providers/recurring_provider.dart';
import 'presentation/providers/settings_provider.dart';

final _log = AppLogger('main');

Future<void> main() async {
  // ignore: avoid_print
  print('>>> [STARTUP] main() started');
  WidgetsFlutterBinding.ensureInitialized();
  // ignore: avoid_print
  print('>>> [STARTUP] WidgetsFlutterBinding initialized');
  ErrorHandler.install();

  final hive = HiveService();
  // ignore: avoid_print
  print('>>> [STARTUP] calling hive.init()');
  await hive.init();
  // ignore: avoid_print
  print('>>> [STARTUP] hive.init() finished');
  await hive.ensureOpen(HiveBoxes.all);
  // ignore: avoid_print
  print('>>> [STARTUP] hive.ensureOpen finished');

  final migration = await HiveMigrator(hive).run();
  if (migration case Err<int>(:final failure)) {
    _log.error('startup migration failed', cause: failure.cause, fields: {'message': failure.message});
  }
  // ignore: avoid_print
  print('>>> [STARTUP] migration finished');

  final expenseRepo = ExpenseRepository(hive);
  final budgetRepo = BudgetRepository(hive);
  final recurringRepo = RecurringRepository(hive);
  final templateRepo = TemplateRepository(hive);
  final settingsRepo = SettingsRepository(hive);
  final currencyRepo = CurrencyRepository(hive);

  final currencyService = CurrencyService(currencyRepo, settingsRepo);
  final budgetService = BudgetService(budgetRepo, expenseRepo, currencyService);
  final analyticsService = AnalyticsService(expenseRepo, settingsRepo, currencyService);
  final csvExportService = CsvExportService(expenseRepo, settingsRepo, currencyService);
  final recurringService = RecurringService(recurringRepo, expenseRepo);

  final notificationGateway =
      AppConfig.notificationsSupported ? LocalNotificationGateway() : NoopNotificationGateway();
  final notificationService = NotificationService(notificationGateway);
  // ignore: avoid_print
  print('>>> [STARTUP] calling notificationService.init()');
  await notificationService.init();
  // ignore: avoid_print
  print('>>> [STARTUP] notificationService.init() finished');

  final recurringResult = await recurringService.materialiseDue();
  if (recurringResult case Err<int>(:final failure)) {
    _log.warn('recurring materialisation failed', {'message': failure.message});
  }
  // ignore: avoid_print
  print('>>> [STARTUP] recurring materialisation finished. Calling runApp()...');

  runApp(
    MultiProvider(
      providers: [
        Provider<HiveService>.value(value: hive),
        Provider<TemplateRepository>.value(value: templateRepo),
        ChangeNotifierProvider(create: (_) => ExpenseProvider(expenseRepo, templateRepo)),
        ChangeNotifierProvider(
          create: (_) => BudgetProvider(budgetRepo, budgetService, notificationService),
        ),
        ChangeNotifierProvider(create: (_) => AnalyticsProvider(analyticsService, settingsRepo)),
        ChangeNotifierProvider(
          create: (_) => RecurringProvider(recurringRepo, recurringService),
        ),
        ChangeNotifierProvider(
          create: (_) => SettingsProvider(
            settingsRepo,
            expenseRepo,
            currencyService,
            currencyRepo,
            notificationService,
            csvExportService,
            hive,
          ),
        ),
      ],
      child: const ExpenseTrackerApp(),
    ),
  );
}
