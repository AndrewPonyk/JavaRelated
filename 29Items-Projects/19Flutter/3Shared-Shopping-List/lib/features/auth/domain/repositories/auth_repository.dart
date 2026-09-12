import '../entities/user_profile.dart';

/// Abstract contract for authentication and household account management
abstract class AuthRepository {
  /// Emits the current user profile whenever auth state changes
  Stream<UserProfile?> get authStateChanges;

  /// Retrieves the current user profile synchronously from cache or memory
  UserProfile? get currentUser;

  /// Performs friction-free anonymous sign-in for instant onboarding
  Future<UserProfile> signInAnonymously();

  /// Links an existing anonymous account with permanent credentials (Email / Password)
  Future<UserProfile> linkWithEmailPassword({
    required String email,
    required String password,
    required String displayName,
  });

  /// Signs in with existing email/password
  Future<UserProfile> signInWithEmailPassword({
    required String email,
    required String password,
  });

  /// Signs in using GitHub OAuth SSO
  Future<UserProfile> signInWithGithub();

  /// Links an existing anonymous account with GitHub credentials
  Future<UserProfile> linkWithGithub();

  /// Updates active shopping list pointer for the user
  Future<void> updateActiveList(String listId);

  /// Signs out and resets local state
  Future<void> signOut();
}
