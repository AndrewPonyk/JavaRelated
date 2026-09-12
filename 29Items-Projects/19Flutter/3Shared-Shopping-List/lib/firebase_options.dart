// File generated for Firebase Configuration.
// ignore_for_file: type=lint
import 'package:firebase_core/firebase_core.dart' show FirebaseOptions;
import 'package:flutter/foundation.dart'
    show defaultTargetPlatform, kIsWeb, TargetPlatform;
import 'core/constants/app_constants.dart';

/// Default [FirebaseOptions] for use with your Firebase apps.
class DefaultFirebaseOptions {
  static FirebaseOptions get currentPlatform {
    if (kIsWeb) {
      return web;
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return android;
      default:
        return web;
    }
  }

  static const FirebaseOptions web = FirebaseOptions(
    apiKey: AppConstants.firebaseApiKey,
    appId: AppConstants.firebaseAppId,
    messagingSenderId: AppConstants.firebaseMessagingSenderId,
    projectId: AppConstants.firebaseProjectId,
    authDomain: AppConstants.firebaseAuthDomain,
    storageBucket: AppConstants.firebaseStorageBucket,
    measurementId: AppConstants.firebaseMeasurementId,
  );

  static const FirebaseOptions android = FirebaseOptions(
    apiKey: AppConstants.firebaseApiKey,
    appId: AppConstants.firebaseAppId,
    messagingSenderId: AppConstants.firebaseMessagingSenderId,
    projectId: AppConstants.firebaseProjectId,
    authDomain: AppConstants.firebaseAuthDomain,
    storageBucket: AppConstants.firebaseStorageBucket,
  );
}
