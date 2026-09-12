import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:sqflite_common_ffi_web/sqflite_ffi_web.dart';
import 'app.dart';
import 'core/services/home_widget_service.dart';
import 'core/services/notification_service.dart';

/// Top-level callback for interactive Home Screen Widget clicks in background
@pragma('vm:entry-point')
Future<void> homeWidgetBackgroundCallback(Uri? uri) async {
  if (uri != null && uri.host == 'togglehabit') {
    final habitId = uri.queryParameters['id'];
    if (habitId != null) {
      // Background habit toggle processing
    }
  }
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // SQLite FFI initialization for Web, Windows, and Linux
  if (kIsWeb) {
    databaseFactory = databaseFactoryFfiWeb;
  } else if (Platform.isWindows || Platform.isLinux) {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  }

  // Initialize platform notification services (Android / iOS / macOS / Linux)
  final notificationService = NotificationService.instance;
  await notificationService.initialize();
  await notificationService.requestPermissions();

  // Initialize native home widget engine (Android / iOS)
  final homeWidgetService = HomeWidgetService.instance;
  await homeWidgetService.initialize();
  await homeWidgetService.registerInteractivityCallback(
    homeWidgetBackgroundCallback,
  );

  runApp(const ProviderScope(child: HabitTrackerApp()));
}
