import '../../domain/entities/user_profile.dart';

class UserModel extends UserProfile {
  const UserModel({
    required super.uid,
    super.displayName,
    super.email,
    super.isAnonymous = true,
    super.activeListId,
    super.fcmTokens = const [],
    required super.createdAt,
  });

  factory UserModel.fromMap(Map<String, dynamic> data, String uid) {
    DateTime parseDate(dynamic timestamp) {
      if (timestamp == null) return DateTime.now();
      if (timestamp is DateTime) return timestamp;
      try {
        final dynamic t = timestamp;
        final dynamic res = t.toDate();
        if (res is DateTime) return res;
        return DateTime.now();
      } catch (_) {
        return DateTime.now();
      }
    }

    return UserModel(
      uid: uid,
      displayName: data['displayName'] as String?,
      email: data['email'] as String?,
      isAnonymous: data['isAnonymous'] as bool? ?? true,
      activeListId: data['activeListId'] as String?,
      fcmTokens: List<String>.from(data['fcmTokens'] as List? ?? []),
      createdAt: parseDate(data['createdAt']),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'displayName': displayName,
      'email': email,
      'isAnonymous': isAnonymous,
      'activeListId': activeListId,
      'fcmTokens': fcmTokens,
      'createdAt': createdAt.toIso8601String(),
    };
  }
}
