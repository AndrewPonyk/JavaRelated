/// Levelled logger with **mandatory release-mode redaction**.
///
/// Expense amounts and note text are the most sensitive data this app holds. A
/// log line that would embarrass the user if screenshotted does not belong in a
/// release build. See ARCHITECTURE §2.6 "Logging rules".
library;

import 'dart:developer' as developer;

import 'package:flutter/foundation.dart';

enum LogLevel {
  trace(0, 'TRACE'),
  debug(1, 'DEBUG'),
  info(2, 'INFO'),
  warn(3, 'WARN'),
  error(4, 'ERROR');

  const LogLevel(this.severity, this.label);
  final int severity;
  final String label;
}

class AppLogger {
  AppLogger(this.tag);

  final String tag;

  /// trace/debug are stripped in release; info+ ships.
  static LogLevel minLevel = kReleaseMode ? LogLevel.info : LogLevel.trace;

  /// When true, [redact] blanks its input. Always true in release.
  static bool redactSensitive = kReleaseMode;

  /// Wrap any user-provided or monetary value passed to a log call.
  ///
  /// ```dart
  /// _log.info('saved expense', {'amount': AppLogger.redact(m.formatted)});
  /// ```
  static String redact(Object? value) {
    if (value == null) return 'null';
    if (!redactSensitive) return value.toString();
    return '<redacted:${value.toString().length}>';
  }

  void trace(String message, [Map<String, Object?>? fields]) =>
      _emit(LogLevel.trace, message, fields);

  void debug(String message, [Map<String, Object?>? fields]) =>
      _emit(LogLevel.debug, message, fields);

  void info(String message, [Map<String, Object?>? fields]) =>
      _emit(LogLevel.info, message, fields);

  void warn(String message, [Map<String, Object?>? fields]) =>
      _emit(LogLevel.warn, message, fields);

  void error(
    String message, {
    Object? cause,
    StackTrace? stackTrace,
    Map<String, Object?>? fields,
  }) {
    _emit(LogLevel.error, message, fields, cause: cause, stackTrace: stackTrace);
  }

  void _emit(
    LogLevel level,
    String message,
    Map<String, Object?>? fields, {
    Object? cause,
    StackTrace? stackTrace,
  }) {
    if (level.severity < minLevel.severity) return;
    final suffix = (fields == null || fields.isEmpty)
        ? ''
        : ' ${fields.entries.map((e) => '${e.key}=${e.value}').join(' ')}';
    developer.log(
      '$message$suffix',
      name: '${level.label}/$tag',
      level: _developerLevel(level),
      error: cause,
      stackTrace: stackTrace,
    );
  }

  static int _developerLevel(LogLevel level) => switch (level) {
        LogLevel.trace => 300,
        LogLevel.debug => 500,
        LogLevel.info => 800,
        LogLevel.warn => 900,
        LogLevel.error => 1000,
      };
}
