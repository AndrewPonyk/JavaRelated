abstract class Failure {
  final String message;
  const Failure(this.message);

  @override
  String toString() => message;
}

class ServerFailure extends Failure {
  const ServerFailure(
      [super.message = 'A server error occurred. Please try again.']);
}

class OfflineFailure extends Failure {
  const OfflineFailure(
      [super.message = 'No internet connection. Changes saved locally.']);
}

class PermissionFailure extends Failure {
  const PermissionFailure(
      [super.message = 'You do not have permission to access this list.']);
}

class NotFoundFailure extends Failure {
  const NotFoundFailure(
      [super.message = 'Requested list or item was not found.']);
}
