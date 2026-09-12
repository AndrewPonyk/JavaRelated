class AppConstants {
  static const String appName = 'Shared Shopping List';
  static const String listsCollection = 'shopping_lists';
  static const String itemsSubcollection = 'items';
  static const String usersCollection = 'users';
  static const String invitationsCollection = 'invitations';

  // Firebase Production Configuration
  static const String firebaseApiKey =
      'AIzaSyC646fpAsjOPBLbh69YbHU7JSyShaxzhds';
  static const String firebaseProjectId = 'shared-shopping-list-38e5b';
  static const String firebaseAuthDomain =
      'shared-shopping-list-38e5b.firebaseapp.com';
  static const String firebaseStorageBucket =
      'shared-shopping-list-38e5b.firebasestorage.app';
  static const String firebaseMessagingSenderId = '327974635';
  static const String firebaseAppId = '1:327974635:web:fdbf588253499da33408ae';
  static const String firebaseMeasurementId = 'G-0YX9QCT71G';

  // URLs & Endpoints
  static const String firebaseAuthHandlerUrl =
      'https://shared-shopping-list-38e5b.firebaseapp.com/__/auth/handler';
  static const String firebaseWebAppUrl =
      'https://shared-shopping-list-38e5b.web.app';
  static const String firestoreRestBaseUrl =
      'https://firestore.googleapis.com/v1/projects/shared-shopping-list-38e5b/databases/(default)/documents';

  // Cache & Network thresholds
  static const int maxFirestoreCacheBytes = 104857600; // 100 MB
  static const Duration syncDebounceDuration = Duration(milliseconds: 300);
}
