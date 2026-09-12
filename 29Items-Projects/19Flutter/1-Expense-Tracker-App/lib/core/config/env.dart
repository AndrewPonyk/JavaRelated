/// Compile-time configuration.
///
/// Flutter has **no runtime `.env`** — a bundled `.env` asset is readable by
/// anyone who unzips the APK, so it is not a secret store. Everything here comes
/// from `--dart-define` / `--dart-define-from-file=.env` and is baked into the
/// binary at build time. See docs/TECH-NOTES.md §3.4 and `.env.example`.
///
/// `String.fromEnvironment` must be `const` — that is why each value is a
/// top-level const and not read from a map.
library;

enum AppFlavor { dev, staging, prod }

abstract final class Env {
  static const String _flavorRaw =
      String.fromEnvironment('APP_FLAVOR', defaultValue: 'dev');

  static AppFlavor get flavor => switch (_flavorRaw) {
        'prod' => AppFlavor.prod,
        'staging' || 'stg' => AppFlavor.staging,
        _ => AppFlavor.dev,
      };

  static bool get isDev => flavor == AppFlavor.dev;
  static bool get isProd => flavor == AppFlavor.prod;

  /// FX rates endpoint. Empty string disables live rates entirely — the app then
  /// runs on manually entered rates, fully offline.
  static const String fxApiBaseUrl =
      String.fromEnvironment('FX_API_BASE_URL', defaultValue: '');

  /// Optional FX provider key.
  ///
  /// SECURITY: this ships inside the binary and is extractable. Only ever use a
  /// rate-limit-scoped key here, never a billable or privileged secret
  /// (ARCHITECTURE §2.5 "Secret management").
  static const String fxApiKey =
      String.fromEnvironment('FX_API_KEY', defaultValue: '');

  static bool get fxEnabled => fxApiBaseUrl.isNotEmpty;

  /// Seed demo expenses on first run — dev convenience only.
  static const bool seedDemoData =
      bool.fromEnvironment('SEED_DEMO_DATA', defaultValue: false);

  /// Overrides the log floor; empty means "decide from build mode".
  static const String logLevel = String.fromEnvironment('LOG_LEVEL', defaultValue: '');

  /// TODO(phase3): flip to true once Hive encryption + Keystore key storage land.
  static const bool encryptBoxes =
      bool.fromEnvironment('ENCRYPT_BOXES', defaultValue: false);

  /// Human-readable dump for the settings screen's diagnostics tile.
  /// Deliberately omits [fxApiKey].
  static Map<String, Object?> describe() => <String, Object?>{
        'flavor': flavor.name,
        'fxEnabled': fxEnabled,
        'fxApiBaseUrl': fxApiBaseUrl.isEmpty ? '(unset)' : fxApiBaseUrl,
        'fxApiKeySet': fxApiKey.isNotEmpty,
        'seedDemoData': seedDemoData,
        'encryptBoxes': encryptBoxes,
      };
}
