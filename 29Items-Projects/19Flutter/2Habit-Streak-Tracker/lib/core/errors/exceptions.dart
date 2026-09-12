/// Infrastructure and platform level exceptions
class AppDatabaseException implements Exception {
  final String message;
  final Object? cause;
  const AppDatabaseException(this.message, [this.cause]);

  @override
  String toString() => 'AppDatabaseException: $message ${cause ?? ''}';
}

class NotificationException implements Exception {
  final String message;
  const NotificationException(this.message);

  @override
  String toString() => 'NotificationException: $message';
}

class HomeWidgetException implements Exception {
  final String message;
  const HomeWidgetException(this.message);

  @override
  String toString() => 'HomeWidgetException: $message';
}

class ValidationException implements Exception {
  final String message;
  const ValidationException(this.message);

  @override
  String toString() => 'ValidationException: $message';
}
