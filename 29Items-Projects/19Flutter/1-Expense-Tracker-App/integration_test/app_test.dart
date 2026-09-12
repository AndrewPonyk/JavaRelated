/// End-to-end check that the real composition root boots and that adding an
/// expense from the UI is reflected everywhere that reads it: the expense
/// list, the analytics total/breakdown, and (transitively) budget progress.
///
/// Deliberately mirrors `main()` wiring rather than importing it directly —
/// `main()` calls `runApp`, which this test replaces with `pumpWidget` so
/// `WidgetTester` can drive it — but every repository/service/provider is
/// constructed exactly the way `lib/main.dart` does, against a temp Hive
/// directory instead of the device's real one (docs/TECH-NOTES.md §3.2: real
/// Hive, never mocked). `NoopNotificationGateway` replaces the platform
/// plugin gateway so this runs the same on every CI target.
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:integration_test/integration_test.dart';
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
import 'package:expense_tracker/data/repositories/recurring_repository.dart';
import 'package:expense_tracker/data/repositories/settings_repository.dart';
import 'package:expense_tracker/data/repositories/template_repository.dart';
import 'package:expense_tracker/domain/services/analytics_service.dart';
import 'package:expense_tracker/domain/services/budget_service.dart';
import 'package:expense_tracker/domain/services/csv_export_service.dart';
import 'package:expense_tracker/domain/services/currency_service.dart';
import 'package:expense_tracker/domain/services/notification_service.dart';
import 'package:expense_tracker/domain/services/recurring_service.dart';
import 'package:expense_tracker/presentation/providers/analytics_provider.dart';
import 'package:expense_tracker/presentation/providers/budget_provider.dart';
import 'package:expense_tracker/presentation/providers/expense_provider.dart';
import 'package:expense_tracker/presentation/providers/settings_provider.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('expense_tracker_integration_');
    Hive.init(tempDir.path);
    registerHiveAdapters();
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

  /// Builds the exact provider graph `lib/main.dart` builds, then pumps
  /// [ExpenseTrackerApp] — the same widget `runApp` receives in production.
  Future<void> pumpApp(WidgetTester tester) async {
    final hive = HiveService();
    await HiveMigrator(hive).run();

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

    final notificationService = NotificationService(NoopNotificationGateway());
    await notificationService.init();
    await recurringService.materialiseDue();

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
  }

  testWidgets('adding an expense updates the list, analytics total, and category breakdown',
      (tester) async {
    await pumpApp(tester);

    // Empty-state text confirms the app actually booted (all boxes open,
    // migrator ran, categories seeded) rather than crashing during startup.
    expect(find.text('No expenses logged this month.'), findsOneWidget);

    await tester.tap(find.byType(FloatingActionButton));
    await tester.pumpAndSettle();
    expect(find.text('Add expense'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Amount'), '12.50');
    await tester.tap(find.byType(DropdownButtonFormField<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Food').last);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Add expense'));
    await tester.pumpAndSettle();

    // New (non-editing) expenses offer to save as a template before popping
    // back to the list (add_expense_screen.dart's `_offerSaveAsTemplate`).
    expect(find.text('Save as template?'), findsOneWidget);
    await tester.tap(find.text('No thanks'));
    await tester.pumpAndSettle();

    expect(find.text('Add expense'), findsNothing);
    expect(find.text('No expenses logged this month.'), findsNothing);
    expect(find.textContaining('12.50'), findsAtLeastNWidgets(1));

    await tester.tap(find.text('Analytics'));
    await tester.pumpAndSettle();

    expect(find.text('No spending yet this month.'), findsNothing);
    expect(find.textContaining('12.50'), findsAtLeastNWidgets(1));
  });
}
