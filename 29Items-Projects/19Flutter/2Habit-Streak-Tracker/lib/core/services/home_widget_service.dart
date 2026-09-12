import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:home_widget/home_widget.dart';
import '../constants/app_constants.dart';

/// Bridge service syncing habit data to native Android and iOS Home Screen Widgets
class HomeWidgetService {
  static final HomeWidgetService instance = HomeWidgetService._internal();

  HomeWidgetService._internal();

  bool get isSupported => !kIsWeb && (Platform.isAndroid || Platform.isIOS);

  /// Initialize App Group for iOS WidgetKit
  Future<void> initialize() async {
    if (!isSupported) return;
    try {
      await HomeWidget.setAppGroupId(AppConstants.appGroupId);
    } catch (e) {
      debugPrint('HomeWidget initialize note: $e');
    }
  }

  /// Write habit JSON payload to native shared storage and trigger widget update
  Future<void> updateWidgetData(String jsonPayload) async {
    if (!isSupported) return;
    try {
      await HomeWidget.saveWidgetData<String>(
        AppConstants.widgetDataKey,
        jsonPayload,
      );

      await HomeWidget.updateWidget(
        name: AppConstants.androidWidgetName,
        iOSName: AppConstants.iosWidgetKind,
      );
    } catch (e) {
      debugPrint('HomeWidget update note: $e');
    }
  }

  /// Register background callback for interactive widget taps
  Future<void> registerInteractivityCallback(
    Future<void> Function(Uri?) callback,
  ) async {
    if (!isSupported) return;
    try {
      await HomeWidget.registerInteractivityCallback(callback);
    } catch (e) {
      debugPrint('HomeWidget callback note: $e');
    }
  }
}
