/// Global error plumbing + the Failure → user-message mapping.
///
/// Two responsibilities, both from ARCHITECTURE §2.6:
///  1. Install process-wide hooks so nothing is lost silently.
///  2. Translate typed [Failure]s into text a human should actually read.
library;

import 'package:flutter/foundation.dart';

import '../logging/app_logger.dart';
import '../utils/result.dart';
import 'failures.dart';

abstract final class ErrorHandler {
  static final AppLogger _log = AppLogger('ErrorHandler');

  /// Call once from `main()` **before** `runApp`.
  ///
  /// In debug, Flutter's default handler still prints the red screen. In release
  /// the error is logged (and would be forwarded to a crash reporter, if the
  /// privacy decision in PROJECT-PLAN Phase 3 lands on adding one).
  static void install() {
    FlutterError.onError = (FlutterErrorDetails details) {
      // ignore: avoid_print
      print('>>> [FLUTTER ERROR] ${details.exception}\n${details.stack}');
      _log.error(
        'Uncaught Flutter error',
        cause: details.exception,
        stackTrace: details.stack,
        fields: {'library': details.library ?? 'unknown'},
      );
      if (kDebugMode) FlutterError.presentError(details);
    };

    // Errors from the engine / async zones that never reach FlutterError.
    PlatformDispatcher.instance.onError = (Object error, StackTrace stack) {
      // ignore: avoid_print
      print('>>> [ASYNC ERROR] $error\n$stack');
      _log.error('Uncaught async error', cause: error, stackTrace: stack);
      return true; // handled — do not kill the isolate
    };
  }

  /// Runs [action], converting an unexpected throw into an [Err].
  ///
  /// Use this at layer boundaries (repositories, service entry points) so an
  /// exception from a third-party package cannot escape as an untyped throw.
  /// Programmer errors ([StateError], [ArgumentError]) are deliberately
  /// **rethrown in debug** so bugs stay loud — category B in §2.6.
  static Future<Result<T>> guard<T>(
    Future<T> Function() action, {
    required String context,
    Failure Function(Object error, StackTrace stack)? onError,
  }) async {
    try {
      return Ok(await action());
    } on Failure catch (f) {
      return Err(f);
    } catch (error, stack) {
      if (kDebugMode && (error is StateError || error is ArgumentError)) {
        rethrow;
      }
      _log.error('guard[$context] failed', cause: error, stackTrace: stack);
      return Err(
        onError?.call(error, stack) ??
            UnexpectedFailure('$context failed', cause: error, stackTrace: stack),
      );
    }
  }

  /// Synchronous variant of [guard].
  static Result<T> guardSync<T>(
    T Function() action, {
    required String context,
    Failure Function(Object error, StackTrace stack)? onError,
  }) {
    try {
      return Ok(action());
    } on Failure catch (f) {
      return Err(f);
    } catch (error, stack) {
      if (kDebugMode && (error is StateError || error is ArgumentError)) {
        rethrow;
      }
      _log.error('guardSync[$context] failed', cause: error, stackTrace: stack);
      return Err(
        onError?.call(error, stack) ??
            UnexpectedFailure('$context failed', cause: error, stackTrace: stack),
      );
    }
  }

  /// Maps a [Failure] to a message safe to show a user.
  ///
  /// Never leaks a class name, stack trace, or raw exception text — rule 1 of the
  /// user-facing error contract. The `switch` is exhaustive over the sealed
  /// hierarchy, so a new Failure type will not compile until it is handled here.
  static String userMessage(Failure failure) => switch (failure) {
        ValidationFailure(:final message) => message,
        NotFoundFailure() => 'That item no longer exists.',
        StorageFailure() =>
          'Could not save your data. Please try again — if it keeps happening, '
              'restart the app.',
        NetworkFailure(isTimeout: true) =>
          'Exchange rates took too long to load. Showing the last known rates.',
        NetworkFailure() =>
          'Could not reach the exchange-rate service. Showing the last known rates.',
        PermissionFailure(:final permission) =>
          'Permission needed${permission == null ? '' : ' ($permission)'}. '
              'You can enable it in system settings.',
        ExportFailure() => 'Export failed. Please try again.',
        UnexpectedFailure() => 'Something went wrong. Please try again.',
      };
}
