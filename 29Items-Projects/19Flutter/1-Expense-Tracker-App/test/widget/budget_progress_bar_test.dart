import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:expense_tracker/core/utils/money.dart';
import 'package:expense_tracker/data/models/budget.dart';
import 'package:expense_tracker/domain/services/budget_service.dart';
import 'package:expense_tracker/presentation/widgets/budget_progress_bar.dart';

void main() {
  Budget makeBudget({String? categoryId}) {
    return Budget(
      id: 'budget_2026-03_${categoryId ?? 'overall'}',
      month: DateTime(2026, 3),
      limitMinor: 10000,
      currencyCode: 'USD',
      categoryId: categoryId,
    );
  }

  Future<void> pump(WidgetTester tester, BudgetProgress progress, {String? label}) {
    return tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: BudgetProgressBar(progress: progress, label: label),
        ),
      ),
    );
  }

  // The widget's own invariant (docs/TECH-NOTES.md §3.6): safe/warning/over
  // must always be distinguishable by icon + text label, never colour alone.
  // Each case below asserts a different icon and a different status string.

  testWidgets('safe status shows the check icon and "On track" label', (tester) async {
    final progress = BudgetProgress(
      budget: makeBudget(),
      spent: Money(0, 'USD'),
      ratio: 0.0,
      status: BudgetStatus.safe,
    );

    await pump(tester, progress);

    expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);
    expect(find.text('On track'), findsOneWidget);
    expect(find.byIcon(Icons.warning_amber_rounded), findsNothing);
    expect(find.byIcon(Icons.error_outline), findsNothing);
  });

  testWidgets('warning status shows the warning icon and "Nearing limit" label', (tester) async {
    final progress = BudgetProgress(
      budget: makeBudget(),
      spent: Money(8500, 'USD'),
      ratio: 0.85,
      status: BudgetStatus.warning,
    );

    await pump(tester, progress);

    expect(find.byIcon(Icons.warning_amber_rounded), findsOneWidget);
    expect(find.text('Nearing limit'), findsOneWidget);
    expect(find.byIcon(Icons.check_circle_outline), findsNothing);
    expect(find.byIcon(Icons.error_outline), findsNothing);
  });

  testWidgets('over status shows the error icon and "Over budget" label', (tester) async {
    final progress = BudgetProgress(
      budget: makeBudget(),
      spent: Money(12000, 'USD'),
      ratio: 1.2,
      status: BudgetStatus.over,
    );

    await pump(tester, progress);

    expect(find.byIcon(Icons.error_outline), findsOneWidget);
    expect(find.text('Over budget'), findsOneWidget);
    expect(find.byIcon(Icons.check_circle_outline), findsNothing);
    expect(find.byIcon(Icons.warning_amber_rounded), findsNothing);
  });

  testWidgets('progress bar value is clamped to 1.0 when over budget', (tester) async {
    final progress = BudgetProgress(
      budget: makeBudget(),
      spent: Money(20000, 'USD'),
      ratio: 2.0,
      status: BudgetStatus.over,
    );

    await pump(tester, progress);

    final indicator = tester.widget<LinearProgressIndicator>(find.byType(LinearProgressIndicator));
    expect(indicator.value, 1.0);
  });

  testWidgets('title falls back to "Overall" for a whole-month budget with no label', (tester) async {
    final progress = BudgetProgress(
      budget: makeBudget(),
      spent: Money(0, 'USD'),
      ratio: 0.0,
      status: BudgetStatus.safe,
    );

    await pump(tester, progress);

    expect(find.text('Overall'), findsOneWidget);
  });

  testWidgets('an explicit label overrides the fallback title', (tester) async {
    final progress = BudgetProgress(
      budget: makeBudget(categoryId: 'cat_food'),
      spent: Money(0, 'USD'),
      ratio: 0.0,
      status: BudgetStatus.safe,
    );

    await pump(tester, progress, label: 'Food');

    expect(find.text('Food'), findsOneWidget);
    expect(find.text('cat_food'), findsNothing);
  });

  testWidgets('spent-of-limit text is formatted with both amounts', (tester) async {
    final progress = BudgetProgress(
      budget: makeBudget(),
      spent: Money(4250, 'USD'),
      ratio: 0.425,
      status: BudgetStatus.safe,
    );

    await pump(tester, progress);

    expect(find.text('USD 42.50 of USD 100.00'), findsOneWidget);
  });

  testWidgets('tapping the card invokes onTap', (tester) async {
    var tapped = false;
    final progress = BudgetProgress(
      budget: makeBudget(),
      spent: Money(0, 'USD'),
      ratio: 0.0,
      status: BudgetStatus.safe,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: BudgetProgressBar(progress: progress, onTap: () => tapped = true),
        ),
      ),
    );
    await tester.tap(find.byType(InkWell));
    await tester.pump();

    expect(tapped, isTrue);
  });
}
