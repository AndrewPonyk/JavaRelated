/// Runtime-tunable feature flags, derived from [Env] plus platform capability.
///
/// Kept separate from [Env] so the UI asks "is this feature on?" rather than
/// "which flavour am I?" — the latter leaks build concerns into widgets.
library;

import 'package:flutter/foundation.dart';

import 'env.dart';

abstract final class AppConfig {
  /// `flutter_local_notifications` supports Android/iOS/macOS/Linux — but NOT
  /// Web or Windows. Both are dev-only targets here, so guard rather than crash.
  /// See docs/TECH-NOTES.md §3.6.
  static bool get notificationsSupported {
    if (kIsWeb) return false;
    return defaultTargetPlatform == TargetPlatform.android ||
        defaultTargetPlatform == TargetPlatform.iOS ||
        defaultTargetPlatform == TargetPlatform.macOS;
  }

  /// Share sheet / file export availability.
  static bool get exportSupported => !kIsWeb;

  static bool get liveFxRatesEnabled => Env.fxEnabled;

  /// Diagnostics tile in Settings — dev/staging only.
  static bool get showDiagnostics => !Env.isProd;

  /// Multi-currency UI. Always on: it is a core requirement (travellers), and it
  /// works offline with manually entered or cached rates.
  static const bool multiCurrencyEnabled = true;

  /// TODO(phase3): biometric app lock via `local_auth`.
  static const bool appLockEnabled = false;
}
