// Smoke test: boots the real app (Hive pointed at a temp directory, no
// mocking needed — see `data/datasources/hive_service.dart` doc comment) and
// checks the four-tab home shell renders. Notifications always use the noop
// gateway here regardless of `AppConfig.notificationsSupported`, since
// `flutter test` reports `TargetPlatform.android` by default and would
// otherwise reach for the real `flutter_local_notifications` plugin.

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:provider/provider.dart';

import 'package:expense_tracker/app.dart';
import 'package:expense_tracker/core/constants/hive_boxes.dart';
import 'package:expense_tracker/data/adapters/hive_adapters.dart';
import 'package:expense_tracker/data/datasources/hive_service.dart';
import 'package:expense_tracker/data/migrations/hive_migrator.dart';
import 'package:expense_tracker/data/models/app_settings.dart';
import 'package:expense_tracker/data/models/budget.dart';
import 'package:expense_tracker/data/models/currency_rate.dart';
import 'package:expense_tracker/data/models/expense.dart';
import 'package:expense_tracker/data/models/expense_category.dart';
import 'package:expense_tracker/data/models/quick_template.dart';
import 'package:expense_tracker/data/models/recurring_expense.dart';
import 'package:expense_tracker/data/repositories/budget_repository.dart';
import 'package:expense_tracker/data/repositories/currency_repository.dart';
import 'package:expense_tracker/data/repositories/expense_repository.dart';
import 'package:expense_tracker/data/repositories/settings_repository.dart';
import 'package:expense_tracker/data/repositories/template_repository.dart';
import 'package:expense_tracker/domain/services/analytics_service.dart';
import 'package:expense_tracker/domain/services/budget_service.dart';
import 'package:expense_tracker/domain/services/csv_export_service.dart';
import 'package:expense_tracker/domain/services/currency_service.dart';
import 'package:expense_tracker/domain/services/notification_service.dart';
import 'package:expense_tracker/presentation/providers/analytics_provider.dart';
import 'package:expense_tracker/presentation/providers/budget_provider.dart';
import 'package:expense_tracker/presentation/providers/expense_provider.dart';
import 'package:expense_tracker/presentation/providers/settings_provider.dart';

void main() {
  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('expense_tracker_test_');
    Hive.init(tempDir.path);
    registerHiveAdapters();
    // Opened with each model's real type (not `hive.init()`, which would call
    // `Hive.initFlutter()` and reach for the unmocked path_provider channel) —
    // matches HiveService._open, so HiveService's getters find boxes of the
    // type they expect.
    await Hive.openBox<Expense>(HiveBoxes.expenses);
    await Hive.openBox<ExpenseCategory>(HiveBoxes.categories);
    await Hive.openBox<Budget>(HiveBoxes.budgets);
    await Hive.openBox<RecurringExpense>(HiveBoxes.recurring);
    await Hive.openBox<QuickTemplate>(HiveBoxes.templates);
    await Hive.openBox<AppSettings>(HiveBoxes.settings);
    await Hive.openBox<CurrencyRate>(HiveBoxes.rates);
  });

  tearDown(() async {
    await Hive.deleteFromDisk();
    await tempDir.delete(recursive: true);
  });

  testWidgets('home shell renders all four tabs', (WidgetTester tester) async {
    final hive = HiveService();
    // `testWidgets` bodies run inside flutter_test's FakeAsync zone, which
    // never drives the real event loop that real Hive disk writes complete
    // on — `runAsync` steps outside that zone so the migrator's real
    // `box.put` calls actually resolve instead of hanging forever.
    await tester.runAsync(() => HiveMigrator(hive).run());

    final expenseRepo = ExpenseRepository(hive);
    final budgetRepo = BudgetRepository(hive);
    final templateRepo = TemplateRepository(hive);
    final settingsRepo = SettingsRepository(hive);
    final currencyRepo = CurrencyRepository(hive);

    final currencyService = CurrencyService(currencyRepo, settingsRepo);
    final budgetService = BudgetService(budgetRepo, expenseRepo, currencyService);
    final analyticsService = AnalyticsService(expenseRepo, settingsRepo, currencyService);
    final csvExportService = CsvExportService(expenseRepo, settingsRepo, currencyService);
    final notificationService = NotificationService(NoopNotificationGateway());

    await tester.pumpWidget(
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
    await tester.pumpAndSettle();

    expect(find.text('Expenses'), findsWidgets);
    expect(find.text('Budget'), findsOneWidget);
    expect(find.text('Analytics'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });
}
