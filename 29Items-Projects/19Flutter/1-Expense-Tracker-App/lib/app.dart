/// Root widget: theme + navigation only. Everything else lives in providers
/// and screens (ARCHITECTURE §2.2).
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'core/constants/app_constants.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';
import 'presentation/providers/settings_provider.dart';

class ExpenseTrackerApp extends StatelessWidget {
  const ExpenseTrackerApp({super.key});

  @override
  Widget build(BuildContext context) {
    // ignore: avoid_print
    print('>>> [WIDGET] ExpenseTrackerApp.build called');
    final themeModeIndex = context.watch<SettingsProvider>().settings.themeModeIndex;
    // ignore: avoid_print
    print('>>> [WIDGET] themeModeIndex = $themeModeIndex');

    return MaterialApp(
      title: AppConstants.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.values[themeModeIndex],
      initialRoute: AppRoutes.home,
      onGenerateRoute: AppRouter.onGenerateRoute,
    );
  }
}
