class AppConfig {
  const AppConfig({
    required this.apiBaseUrl,
    required this.mapboxAccessToken,
  });

  factory AppConfig.fromEnvironment() {
    return const AppConfig(
      apiBaseUrl: String.fromEnvironment(
        'MOBILE_API_BASE_URL',
        defaultValue: 'http://10.0.2.2:8080',
      ),
      mapboxAccessToken: String.fromEnvironment('MAPBOX_ACCESS_TOKEN'),
    );
  }

  final String apiBaseUrl;
  final String mapboxAccessToken;
}
