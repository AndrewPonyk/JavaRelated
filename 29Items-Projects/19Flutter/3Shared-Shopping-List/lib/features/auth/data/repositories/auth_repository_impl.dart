import 'dart:async';
import 'package:firebase_auth/firebase_auth.dart' as fb;
import 'package:flutter/foundation.dart' show kIsWeb;
import '../../domain/entities/user_profile.dart';
import '../../domain/repositories/auth_repository.dart';
import '../models/user_model.dart';

/// Concrete implementation of AuthRepository supporting live Firebase Auth and offline fallbacks
class AuthRepositoryImpl implements AuthRepository {
  final fb.FirebaseAuth? _firebaseAuth;
  UserProfile? _currentUser;
  final StreamController<UserProfile?> _authController =
      StreamController<UserProfile?>.broadcast();
  StreamSubscription<fb.User?>? _fbAuthSubscription;

  AuthRepositoryImpl({
    fb.FirebaseAuth? firebaseAuth,
    UserProfile? initialUser,
  }) : _firebaseAuth = firebaseAuth {
    if (_firebaseAuth != null) {
      final currentFbUser = _firebaseAuth.currentUser;
      if (currentFbUser != null) {
        _currentUser = _mapFirebaseUser(currentFbUser);
      }
      _fbAuthSubscription = _firebaseAuth.authStateChanges().listen((fbUser) {
        if (fbUser != null) {
          final user = _mapFirebaseUser(fbUser);
          _currentUser = user;
          _authController.add(user);
        } else {
          _currentUser = null;
          _authController.add(null);
        }
      });
    } else {
      _currentUser = initialUser ??
          UserProfile(
            uid: 'user-001',
            displayName: 'Shopper Alex',
            email: 'alex@family.home',
            isAnonymous: false,
            activeListId: 'list-001',
            createdAt: DateTime.now(),
          );
    }
  }

  UserProfile _mapFirebaseUser(fb.User fbUser) {
    String? displayName = fbUser.displayName;
    String? email = fbUser.email;
    for (final info in fbUser.providerData) {
      if ((displayName == null || displayName.isEmpty) &&
          info.displayName != null &&
          info.displayName!.isNotEmpty) {
        displayName = info.displayName;
      }
      if ((email == null || email.isEmpty) &&
          info.email != null &&
          info.email!.isNotEmpty) {
        email = info.email;
      }
    }

    return UserModel(
      uid: fbUser.uid,
      displayName: displayName ??
          (fbUser.isAnonymous ? 'Guest Shopper' : 'GitHub Shopper'),
      email: email ?? (fbUser.isAnonymous ? null : 'GitHub Account Connected'),
      isAnonymous: fbUser.isAnonymous,
      activeListId: _currentUser?.activeListId ?? 'list-001',
      createdAt: _currentUser?.createdAt ?? DateTime.now(),
    );
  }

  @override
  Stream<UserProfile?> get authStateChanges => _authController.stream;

  @override
  UserProfile? get currentUser => _currentUser;

  @override
  Future<UserProfile> signInAnonymously() async {
    if (_firebaseAuth != null) {
      final userCredential = await _firebaseAuth.signInAnonymously();
      final user = _mapFirebaseUser(userCredential.user!);
      _currentUser = user;
      _authController.add(user);
      return user;
    }

    final newUser = UserModel(
      uid: 'anon-${DateTime.now().millisecondsSinceEpoch}',
      displayName: 'Guest Shopper',
      isAnonymous: true,
      activeListId: 'list-001',
      createdAt: DateTime.now(),
    );
    _currentUser = newUser;
    _authController.add(newUser);
    return newUser;
  }

  @override
  Future<UserProfile> linkWithEmailPassword({
    required String email,
    required String password,
    required String displayName,
  }) async {
    if (_firebaseAuth != null) {
      final currentFbUser = _firebaseAuth.currentUser;
      final credential = fb.EmailAuthProvider.credential(
        email: email,
        password: password,
      );
      if (currentFbUser != null && currentFbUser.isAnonymous) {
        final userCredential =
            await currentFbUser.linkWithCredential(credential);
        await userCredential.user?.updateDisplayName(displayName);
        final user = _mapFirebaseUser(userCredential.user ?? currentFbUser);
        _currentUser = user;
        _authController.add(user);
        return user;
      } else {
        final userCredential =
            await _firebaseAuth.signInWithCredential(credential);
        final user = _mapFirebaseUser(userCredential.user!);
        _currentUser = user;
        _authController.add(user);
        return user;
      }
    }

    final linkedUser = UserModel(
      uid: _currentUser?.uid ?? 'user-${DateTime.now().millisecondsSinceEpoch}',
      displayName: displayName,
      email: email,
      isAnonymous: false,
      activeListId: _currentUser?.activeListId ?? 'list-001',
      createdAt: _currentUser?.createdAt ?? DateTime.now(),
    );
    _currentUser = linkedUser;
    _authController.add(linkedUser);
    return linkedUser;
  }

  @override
  Future<UserProfile> signInWithEmailPassword({
    required String email,
    required String password,
  }) async {
    if (_firebaseAuth != null) {
      final userCredential = await _firebaseAuth.signInWithEmailAndPassword(
        email: email,
        password: password,
      );
      final user = _mapFirebaseUser(userCredential.user!);
      _currentUser = user;
      _authController.add(user);
      return user;
    }

    final user = UserModel(
      uid: 'user-${email.hashCode.abs()}',
      displayName: email.split('@').first,
      email: email,
      isAnonymous: false,
      activeListId: 'list-001',
      createdAt: DateTime.now(),
    );
    _currentUser = user;
    _authController.add(user);
    return user;
  }

  @override
  Future<UserProfile> signInWithGithub() async {
    if (_firebaseAuth != null) {
      final provider = fb.GithubAuthProvider();
      provider.addScope('read:user');
      provider.addScope('user:email');
      final fb.UserCredential userCredential;
      if (kIsWeb) {
        userCredential = await _firebaseAuth.signInWithPopup(provider);
      } else {
        userCredential = await _firebaseAuth.signInWithProvider(provider);
      }
      final user = _mapFirebaseUser(userCredential.user!);
      _currentUser = user;
      _authController.add(user);
      return user;
    }

    final user = UserModel(
      uid: 'gh-${DateTime.now().millisecondsSinceEpoch}',
      displayName: 'GitHub Developer',
      email: 'dev@github.com',
      isAnonymous: false,
      activeListId: _currentUser?.activeListId ?? 'list-001',
      createdAt: DateTime.now(),
    );
    _currentUser = user;
    _authController.add(user);
    return user;
  }

  @override
  Future<UserProfile> linkWithGithub() async {
    if (_firebaseAuth != null) {
      final provider = fb.GithubAuthProvider();
      provider.addScope('read:user');
      provider.addScope('user:email');
      final currentFbUser = _firebaseAuth.currentUser;
      if (currentFbUser != null && currentFbUser.isAnonymous) {
        final fb.UserCredential userCredential;
        if (kIsWeb) {
          userCredential = await currentFbUser.linkWithPopup(provider);
        } else {
          userCredential = await currentFbUser.linkWithProvider(provider);
        }
        final user = _mapFirebaseUser(userCredential.user ?? currentFbUser);
        _currentUser = user;
        _authController.add(user);
        return user;
      } else {
        return signInWithGithub();
      }
    }

    final linkedUser = UserModel(
      uid: _currentUser?.uid ?? 'gh-${DateTime.now().millisecondsSinceEpoch}',
      displayName: _currentUser?.displayName ?? 'GitHub Developer',
      email: 'dev@github.com',
      isAnonymous: false,
      activeListId: _currentUser?.activeListId ?? 'list-001',
      createdAt: _currentUser?.createdAt ?? DateTime.now(),
    );
    _currentUser = linkedUser;
    _authController.add(linkedUser);
    return linkedUser;
  }

  @override
  Future<void> updateActiveList(String listId) async {
    if (_currentUser != null) {
      _currentUser = _currentUser!.copyWith(activeListId: listId);
      _authController.add(_currentUser);
    }
  }

  @override
  Future<void> signOut() async {
    if (_firebaseAuth != null) {
      await _firebaseAuth.signOut();
    }
    _currentUser = null;
    _authController.add(null);
  }

  void dispose() {
    _fbAuthSubscription?.cancel();
    _authController.close();
  }
}
