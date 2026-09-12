import 'dart:async';

/// Service handling App Links & Universal Links for invitation redemption
class DynamicLinkService {
  final StreamController<String> _inviteTokenController =
      StreamController<String>.broadcast();

  Stream<String> get inviteTokens => _inviteTokenController.stream;

  /// Simulates or processes an incoming deep link URI
  void handleIncomingUri(Uri uri) {
    // Expected patterns:
    // https://shoppinglist.page.link/join?token=XYZ
    // https://shoppinglist.app/invite?token=XYZ
    final token = uri.queryParameters['token'];
    if (token != null && token.isNotEmpty) {
      _inviteTokenController.add(token);
    }
  }

  void dispose() {
    _inviteTokenController.close();
  }
}
