import 'package:flutter_test/flutter_test.dart';
import 'package:shared_shopping_list/features/auth/data/models/user_model.dart';
import 'package:shared_shopping_list/features/auth/domain/entities/user_profile.dart';

void main() {
  group('UserModel', () {
    final tDateTime = DateTime(2026, 9, 6, 10, 0);
    final tModel = UserModel(
      uid: 'user-001',
      displayName: 'Alex',
      email: 'alex@example.com',
      isAnonymous: false,
      activeListId: 'list-001',
      fcmTokens: const ['fcm-token-1'],
      createdAt: tDateTime,
    );

    test('should be a subclass of UserProfile entity', () {
      expect(tModel, isA<UserProfile>());
    });

    test('fromMap should parse valid map properly', () {
      final map = {
        'displayName': 'Alex',
        'email': 'alex@example.com',
        'isAnonymous': false,
        'activeListId': 'list-001',
        'fcmTokens': ['fcm-token-1'],
        'createdAt': tDateTime,
      };

      final result = UserModel.fromMap(map, 'user-001');

      expect(result.uid, 'user-001');
      expect(result.email, 'alex@example.com');
      expect(result.isAnonymous, isFalse);
    });

    test('toMap should serialize user profile', () {
      final map = tModel.toMap();
      expect(map['email'], 'alex@example.com');
      expect(map['isAnonymous'], isFalse);
    });
  });
}
