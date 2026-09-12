/// Immutable domain entity representing an authenticated household user
class UserProfile {
  final String uid;
  final String? displayName;
  final String? email;
  final bool isAnonymous;
  final String? activeListId;
  final List<String> fcmTokens;
  final DateTime createdAt;

  const UserProfile({
    required this.uid,
    this.displayName,
    this.email,
    this.isAnonymous = true,
    this.activeListId,
    this.fcmTokens = const [],
    required this.createdAt,
  });

  UserProfile copyWith({
    String? uid,
    String? displayName,
    String? email,
    bool? isAnonymous,
    String? activeListId,
    List<String>? fcmTokens,
    DateTime? createdAt,
  }) {
    return UserProfile(
      uid: uid ?? this.uid,
      displayName: displayName ?? this.displayName,
      email: email ?? this.email,
      isAnonymous: isAnonymous ?? this.isAnonymous,
      activeListId: activeListId ?? this.activeListId,
      fcmTokens: fcmTokens ?? this.fcmTokens,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is UserProfile &&
          runtimeType == other.runtimeType &&
          uid == other.uid &&
          email == other.email &&
          isAnonymous == other.isAnonymous;

  @override
  int get hashCode => uid.hashCode ^ email.hashCode ^ isAnonymous.hashCode;
}
