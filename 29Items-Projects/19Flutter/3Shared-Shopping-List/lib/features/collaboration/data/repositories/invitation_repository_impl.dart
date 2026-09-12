import '../../domain/entities/list_invitation.dart';
import '../../domain/repositories/invitation_repository.dart';
import '../models/invitation_model.dart';

class InvitationRepositoryImpl implements InvitationRepository {
  final Map<String, InvitationModel> _invitationsDb = {};

  @override
  Future<ListInvitation> createInvitation({
    required String listId,
    required String role,
    int expiresInDays = 7,
  }) async {
    final token =
        'inv_${DateTime.now().millisecondsSinceEpoch}_${listId.substring(0, 4)}';
    final inviteUrl = 'https://shoppinglist.page.link/join?token=$token';
    final expiresAt = DateTime.now().add(Duration(days: expiresInDays));

    final invite = InvitationModel(
      token: token,
      listId: listId,
      listName: 'Family Grocery Run',
      invitedByUid: 'user-001',
      invitedByName: 'Alex',
      role: role,
      maxUses: 5,
      usedCount: 0,
      expiresAt: expiresAt,
      inviteUrl: inviteUrl,
    );

    _invitationsDb[token] = invite;
    return invite;
  }

  @override
  Future<String> redeemInvitation(String token) async {
    final invite = _invitationsDb[token];
    if (invite == null) {
      // In production, calls Cloud Function redeemInvitation
      return 'list-001';
    }
    if (invite.isExpired) {
      throw Exception('This invitation has expired.');
    }
    if (invite.isExhausted) {
      throw Exception('This invitation has reached its maximum uses.');
    }

    _invitationsDb[token] = InvitationModel(
      token: invite.token,
      listId: invite.listId,
      listName: invite.listName,
      invitedByUid: invite.invitedByUid,
      invitedByName: invite.invitedByName,
      role: invite.role,
      maxUses: invite.maxUses,
      usedCount: invite.usedCount + 1,
      expiresAt: invite.expiresAt,
      inviteUrl: invite.inviteUrl,
    );

    return invite.listId;
  }

  @override
  Future<List<Map<String, String>>> getListMembers(String listId) async {
    return [
      {'uid': 'user-001', 'name': 'Alex (You)', 'role': 'Owner'},
      {'uid': 'user-002', 'name': 'Sam (Roommate)', 'role': 'Editor'},
    ];
  }
}
