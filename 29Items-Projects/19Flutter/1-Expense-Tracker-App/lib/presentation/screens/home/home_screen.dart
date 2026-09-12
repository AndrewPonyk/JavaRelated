/// Bottom-nav shell over the four main tabs. Kept as a single [StatefulWidget]
/// rather than nested `Navigator`s — this app has no cross-tab back-stack
/// requirement, so an [IndexedStack] is the simplest thing that works
/// (ARCHITECTURE §2.2).
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/router/app_router.dart';
import '../../providers/analytics_provider.dart';
import '../../providers/budget_provider.dart';
import '../analytics/analytics_screen.dart';
import '../budget/budget_screen.dart';
import '../expenses/expense_list_screen.dart';
import '../settings/settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _index = 0;

  static const _screens = [
    ExpenseListScreen(),
    BudgetScreen(),
    AnalyticsScreen(),
    SettingsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _index, children: _screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) {
          // Budget/Analytics compute their AsyncState once, on construction
          // (see BudgetProvider/AnalyticsProvider), and don't listen for
          // expenses added from another tab — refresh on the way in so a
          // just-logged expense is reflected without a full app restart.
          switch (i) {
            case 1:
              context.read<BudgetProvider>().load();
            case 2:
              context.read<AnalyticsProvider>().load();
          }
          setState(() => _index = i);
        },
        destinations: const [
          NavigationDestination(icon: Icon(Icons.receipt_long_outlined), label: 'Expenses'),
          NavigationDestination(icon: Icon(Icons.pie_chart_outline), label: 'Budget'),
          NavigationDestination(icon: Icon(Icons.bar_chart_outlined), label: 'Analytics'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), label: 'Settings'),
        ],
      ),
      floatingActionButton: _index == 0
          ? FloatingActionButton(
              heroTag: null,
              onPressed: () => Navigator.of(context).pushNamed(AppRoutes.addExpense),
              child: const Icon(Icons.add),
            )
          : null,
    );
  }
}
