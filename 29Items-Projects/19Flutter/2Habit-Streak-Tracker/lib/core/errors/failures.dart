/// Typed failure objects representing business/domain errors
abstract class Failure {
  final String message;
  const Failure(this.message);

  @override
  String toString() => message;
}

class DatabaseFailure extends Failure {
  const DatabaseFailure(super.message);
}

class ValidationFailure extends Failure {
  const ValidationFailure(super.message);
}

class NotificationFailure extends Failure {
  const NotificationFailure(super.message);
}

class FreezeExceededFailure extends Failure {
  const FreezeExceededFailure([
    super.message = 'No streak freezes remaining for this period',
  ]);
}
