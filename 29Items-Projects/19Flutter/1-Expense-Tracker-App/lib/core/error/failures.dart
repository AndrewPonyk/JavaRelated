/// Sealed failure hierarchy — ARCHITECTURE §2.6 "Category A: expected conditions".
///
/// These are *values*, not exceptions. Repositories and services return them
/// inside a `Result<T>`; they never throw for an expected condition. Because the
/// hierarchy is `sealed`, a `switch` over a Failure is checked exhaustively by
/// the compiler, so adding a new failure type surfaces every place that must
/// handle it — at compile time, not in production.
library;

sealed class Failure {
  const Failure(this.message, {this.cause, this.stackTrace});

  /// Developer-facing description. NOT shown to users verbatim — map it through
  /// `ErrorHandler.userMessage` first.
  final String message;
  final Object? cause;
  final StackTrace? stackTrace;

  @override
  String toString() => '$runtimeType: $message${cause == null ? '' : ' (cause: $cause)'}';
}

/// Input did not satisfy an invariant. Carries [field] so the UI can attach the
/// error to the right form control instead of showing a generic banner.
final class ValidationFailure extends Failure {
  const ValidationFailure(super.message, {this.field, super.cause, super.stackTrace});
  final String? field;
}

/// Hive read/write/box-open problem, or a migration that could not complete.
final class StorageFailure extends Failure {
  const StorageFailure(super.message, {super.cause, super.stackTrace});
}

/// The requested entity does not exist (deleted concurrently, bad id).
final class NotFoundFailure extends Failure {
  const NotFoundFailure(super.message, {this.id, super.cause, super.stackTrace});
  final String? id;
}

/// FX rate fetch failed or timed out. Never fatal — the app falls back to
/// cached rates and shows a staleness banner.
final class NetworkFailure extends Failure {
  const NetworkFailure(super.message, {this.isTimeout = false, super.cause, super.stackTrace});
  final bool isTimeout;
}

/// OS permission denied (notifications, storage) or unavailable on this
/// platform (notifications on Web/Windows).
final class PermissionFailure extends Failure {
  const PermissionFailure(super.message, {this.permission, super.cause, super.stackTrace});
  final String? permission;
}

/// Export/share pipeline failure (file write, share sheet dismissed with error).
final class ExportFailure extends Failure {
  const ExportFailure(super.message, {super.cause, super.stackTrace});
}

/// Escape hatch for genuinely unexpected errors caught at a boundary. If you
/// find yourself constructing many of these, the missing case deserves its own
/// Failure subclass.
final class UnexpectedFailure extends Failure {
  const UnexpectedFailure(super.message, {super.cause, super.stackTrace});
}
