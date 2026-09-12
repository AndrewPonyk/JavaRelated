import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as tz;
import 'package:timezone/timezone.dart' as tz;
import '../constants/app_constants.dart';
import '../utils/date_time_utils.dart';

/// Service managing scheduled local alarms and habit reminders
class NotificationService {
  static final NotificationService instance = NotificationService._internal();
  final FlutterLocalNotificationsPlugin _plugin =
      FlutterLocalNotificationsPlugin();
  bool _isInitialized = false;

  NotificationService._internal();

  bool get isSupported =>
      !kIsWeb &&
      (Platform.isAndroid ||
          Platform.isIOS ||
          Platform.isMacOS ||
          Platform.isLinux);

  /// Initialize local notification plugin and setup channels
  Future<void> initialize({
    void Function(NotificationResponse)? onSelectNotification,
  }) async {
    if (_isInitialized || !isSupported) return;

    try {
      tz.initializeTimeZones();

      const AndroidInitializationSettings androidSettings =
          AndroidInitializationSettings('@mipmap/ic_launcher');

      const DarwinInitializationSettings iosSettings =
          DarwinInitializationSettings(
            requestAlertPermission: false,
            requestBadgePermission: false,
            requestSoundPermission: false,
          );

      const InitializationSettings initSettings = InitializationSettings(
        android: androidSettings,
        iOS: iosSettings,
      );

      await _plugin.initialize(
        initSettings,
        onDidReceiveNotificationResponse: onSelectNotification,
      );

      _isInitialized = true;
    } catch (e) {
      debugPrint('Notification initialization notice: $e');
    }
  }

  /// Request permissions for notifications
  Future<bool> requestPermissions() async {
    if (!isSupported) return true;
    try {
      final androidImpl = _plugin
          .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin
          >();
      final bool? androidGranted = await androidImpl
          ?.requestNotificationsPermission();

      final iosImpl = _plugin
          .resolvePlatformSpecificImplementation<
            IOSFlutterLocalNotificationsPlugin
          >();
      final bool? iosGranted = await iosImpl?.requestPermissions(
        alert: true,
        badge: true,
        sound: true,
      );

      return (androidGranted ?? false) || (iosGranted ?? false);
    } catch (e) {
      debugPrint('Notification permission notice: $e');
      return false;
    }
  }

  /// Schedule a daily recurring habit reminder
  Future<void> scheduleDailyHabitReminder({
    required int habitNotificationId,
    required String habitTitle,
    required String reminderTime, // "HH:mm"
  }) async {
    if (!isSupported) return;
    try {
      final timeParts = DateTimeUtils.parseTimeString(reminderTime);
      if (timeParts == null) return;

      final (hour, minute) = timeParts;

      final now = tz.TZDateTime.now(tz.local);
      var scheduledDate = tz.TZDateTime(
        tz.local,
        now.year,
        now.month,
        now.day,
        hour,
        minute,
      );

      if (scheduledDate.isBefore(now)) {
        scheduledDate = scheduledDate.add(const Duration(days: 1));
      }

      await _plugin.zonedSchedule(
        habitNotificationId,
        'Time for your habit!',
        'Keep your streak alive: $habitTitle',
        scheduledDate,
        const NotificationDetails(
          android: AndroidNotificationDetails(
            AppConstants.reminderChannelId,
            AppConstants.reminderChannelName,
            channelDescription: AppConstants.reminderChannelDescription,
            importance: Importance.high,
            priority: Priority.high,
            icon: '@mipmap/ic_launcher',
          ),
          iOS: DarwinNotificationDetails(
            presentAlert: true,
            presentBadge: true,
            presentSound: true,
          ),
        ),
        androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
        uiLocalNotificationDateInterpretation:
            UILocalNotificationDateInterpretation.absoluteTime,
        matchDateTimeComponents: DateTimeComponents.time,
      );
    } catch (e) {
      debugPrint('Notification scheduling notice: $e');
    }
  }

  /// Cancel reminder for a specific habit
  Future<void> cancelReminder(int habitNotificationId) async {
    if (!isSupported) return;
    try {
      await _plugin.cancel(habitNotificationId);
    } catch (e) {
      debugPrint('Notification cancel notice: $e');
    }
  }

  /// Cancel all scheduled reminders
  Future<void> cancelAllReminders() async {
    if (!isSupported) return;
    try {
      await _plugin.cancelAll();
    } catch (e) {
      debugPrint('Notification cancel-all notice: $e');
    }
  }
}
