import 'package:flutter_test/flutter_test.dart';
import 'package:shared_shopping_list/features/collaboration/data/models/invitation_model.dart';
import 'package:shared_shopping_list/features/collaboration/domain/entities/list_invitation.dart';

void main() {
  group('InvitationModel', () {
    final tExpiresAt = DateTime.now().add(const Duration(days: 7));
    final tModel = InvitationModel(
      token: 'token-abc-123',
      listId: 'list-001',
      listName: 'Groceries',
      invitedByUid: 'user-001',
      invitedByName: 'Alex',
      role: 'editor',
      maxUses: 5,
      usedCount: 1,
      expiresAt: tExpiresAt,
      inviteUrl: 'https://shoppinglist.page.link/join?token=token-abc-123',
    );

    test('should be a subclass of ListInvitation entity', () {
      expect(tModel, isA<ListInvitation>());
      expect(tModel.isValid, isTrue);
      expect(tModel.isExpired, isFalse);
    });

    test('fromMap should parse invitation data', () {
      final map = {
        'listId': 'list-001',
        'listName': 'Groceries',
        'invitedByUid': 'user-001',
        'invitedByName': 'Alex',
        'role': 'editor',
        'maxUses': 5,
        'usedCount': 1,
        'expiresAt': tExpiresAt,
        'inviteUrl': 'https://shoppinglist.page.link/join?token=token-abc-123',
      };

      final result = InvitationModel.fromMap(map, 'token-abc-123');
      expect(result.token, 'token-abc-123');
      expect(result.listName, 'Groceries');
      expect(result.role, 'editor');
    });
  });
}
