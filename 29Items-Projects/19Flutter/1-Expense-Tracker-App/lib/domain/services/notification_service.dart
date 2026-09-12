/// Budget-alert notification logic. Pure business logic — no Flutter, no
/// Hive imports (see docs/PROJECT-PLAN.md §1.3(b)).
///
/// [flutter_local_notifications] is a Flutter plugin, so it cannot be
/// imported here. [NotificationGateway] is the same dependency-inversion
/// trick [HiveService] plays for storage: the domain layer defines the port,
/// and `data/datasources/notification_gateway.dart` is the *only* file
/// allowed to import the plugin.
library;

import '../../core/constants/app_constants.dart';
import 'budget_service.dart';

/// Platform port for showing notifications.
abstract class NotificationGateway {
  Future<void> init();

  /// Returns whether permission was granted.
  Future<bool> requestPermission();

  Future<void> showNow({required int id, required String title, required String body});

  Future<void> cancel(int id);
}

/// No-op gateway for platforms/tests where notifications are unsupported —
/// see `core/config/app_config.dart`'s `notificationsSupported` (Web/Windows).
class NoopNotificationGateway implements NotificationGateway {
  @override
  Future<void> init() async {}

  @override
  Future<bool> requestPermission() async => false;

  @override
  Future<void> showNow({required int id, required String title, required String body}) async {}

  @override
  Future<void> cancel(int id) async {}
}

class NotificationService {
  NotificationService(this._gateway);

  final NotificationGateway _gateway;

  Future<void> init() => _gateway.init();

  Future<bool> requestPermission() => _gateway.requestPermission();

  /// Fires the budget-threshold alert. Callers (`BudgetProvider`) are expected
  /// to invoke this only on a transition into [BudgetStatus.warning] or
  /// [BudgetStatus.over] — not on every rebuild — so this method itself does
  /// not deduplicate.
  Future<void> notifyBudgetThreshold(BudgetProgress progress) {
    final isOver = progress.status == BudgetStatus.over;
    final title = isOver ? 'Budget exceeded' : 'Approaching budget limit';
    final body = isOver
        ? '${progress.spent.formatted} spent — over the ${progress.budget.limit.formatted} budget'
        : '${(progress.ratio * 100).round()}% of the ${progress.budget.limit.formatted} budget used';
    return _gateway.showNow(
      id: AppConstants.notificationIdBudgetAlert,
      title: title,
      body: body,
    );
  }

  static String? _lastRolloverFiredKey;

  Future<void> notifyMonthlyRollover() => _gateway.showNow(
        id: AppConstants.notificationIdMonthlyRollover,
        title: 'New month started',
        body: "Set up this month's budget to stay on track.",
      );

  Future<void> maybeNotifyMonthlyRollover({required bool enabled}) async {
    if (!enabled) return;
    final now = DateTime.now();
    final key = '${now.year}-${now.month}';
    if (now.day <= 3 && _lastRolloverFiredKey != key) {
      _lastRolloverFiredKey = key;
      await notifyMonthlyRollover();
    }
  }
}
