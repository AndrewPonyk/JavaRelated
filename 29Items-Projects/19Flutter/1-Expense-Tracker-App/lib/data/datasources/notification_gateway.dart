/// Concrete [NotificationGateway] backed by `flutter_local_notifications`.
///
/// This is the only file in the app allowed to import
/// `flutter_local_notifications`/`timezone` — see
/// `domain/services/notification_service.dart` for why. Callers should check
/// `AppConfig.notificationsSupported` before constructing this (Web/Windows
/// have no plugin implementation) and use [NoopNotificationGateway] instead.
library;

import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as tz_data;
import 'package:timezone/timezone.dart' as tz;

import '../../domain/services/notification_service.dart';

class LocalNotificationGateway implements NotificationGateway {
  final FlutterLocalNotificationsPlugin _plugin = FlutterLocalNotificationsPlugin();
  bool _initialised = false;

  static const String _channelId = 'budget_alerts';
  static const String _channelName = 'Budget alerts';

  @override
  Future<void> init() async {
    if (_initialised) return;

    tz_data.initializeTimeZones();
    // TODO: resolve the real device timezone (e.g. via `flutter_timezone`) once
    // scheduled — not just "show now" — notifications are needed. UTC is a safe
    // default until then since every alert in phase 1/2 fires immediately.
    tz.setLocalLocation(tz.UTC);

    const androidInit = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosInit = DarwinInitializationSettings();
    const linuxInit = LinuxInitializationSettings(defaultActionName: 'Open');
    await _plugin.initialize(const InitializationSettings(
      android: androidInit,
      iOS: iosInit,
      macOS: iosInit,
      linux: linuxInit,
    ));
    _initialised = true;
  }

  @override
  Future<bool> requestPermission() async {
    if (!_initialised) await init();

    final android = _plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();
    if (android != null) {
      // Android 13+ requires runtime POST_NOTIFICATIONS; older versions grant
      // this implicitly and return true here.
      return await android.requestNotificationsPermission() ?? false;
    }

    final ios = _plugin
        .resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>();
    if (ios != null) {
      return await ios.requestPermissions(alert: true, badge: true, sound: true) ?? false;
    }

    final macos = _plugin
        .resolvePlatformSpecificImplementation<MacOSFlutterLocalNotificationsPlugin>();
    if (macos != null) {
      return await macos.requestPermissions(alert: true, badge: true, sound: true) ?? false;
    }

    return false;
  }

  @override
  Future<void> showNow({required int id, required String title, required String body}) async {
    if (!_initialised) await init();

    const details = NotificationDetails(
      android: AndroidNotificationDetails(
        _channelId,
        _channelName,
        importance: Importance.high,
        priority: Priority.high,
      ),
      iOS: DarwinNotificationDetails(),
      macOS: DarwinNotificationDetails(),
      linux: LinuxNotificationDetails(),
    );
    await _plugin.show(id, title, body, details);
  }

  @override
  Future<void> cancel(int id) => _plugin.cancel(id);
}
