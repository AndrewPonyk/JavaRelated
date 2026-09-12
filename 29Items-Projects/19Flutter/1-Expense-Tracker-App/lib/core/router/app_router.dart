/// Named routes for the app. Kept as a single small table rather than a
/// routing package — three screens don't need one, and adding a fourth is a
/// one-line change here (ARCHITECTURE §2.2).
library;

import 'package:flutter/material.dart';

import '../../data/models/expense.dart';
import '../../presentation/screens/expenses/add_expense_screen.dart';
import '../../presentation/screens/home/home_screen.dart';
import '../../presentation/screens/recurring/recurring_screen.dart';

abstract final class AppRoutes {
  static const String home = '/';
  static const String addExpense = '/expenses/add';
  static const String recurring = '/recurring';
}

/// Typed navigation arguments for [AppRoutes.addExpense]. Passing an
/// [expense] switches the screen into edit mode.
class AddExpenseArgs {
  const AddExpenseArgs({this.expense});
  final Expense? expense;
}

abstract final class AppRouter {
  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case AppRoutes.addExpense:
        final args = settings.arguments as AddExpenseArgs? ?? const AddExpenseArgs();
        return MaterialPageRoute(
          builder: (_) => AddExpenseScreen(args: args),
          settings: settings,
        );
      case AppRoutes.recurring:
        return MaterialPageRoute(
          builder: (_) => const RecurringScreen(),
          settings: settings,
        );
      case AppRoutes.home:
      default:
        return MaterialPageRoute(builder: (_) => const HomeScreen(), settings: settings);
    }
  }
}
