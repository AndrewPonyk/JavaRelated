import '../../domain/entities/list_invitation.dart';

class InvitationModel extends ListInvitation {
  const InvitationModel({
    required super.token,
    required super.listId,
    required super.listName,
    required super.invitedByUid,
    required super.invitedByName,
    super.role = 'editor',
    super.maxUses = 5,
    super.usedCount = 0,
    required super.expiresAt,
    required super.inviteUrl,
  });

  factory InvitationModel.fromMap(Map<String, dynamic> data, String token) {
    DateTime parseDate(dynamic timestamp) {
      if (timestamp == null) return DateTime.now().add(const Duration(days: 7));
      if (timestamp is DateTime) return timestamp;
      try {
        final dynamic t = timestamp;
        final dynamic res = t.toDate();
        if (res is DateTime) return res;
        return DateTime.now().add(const Duration(days: 7));
      } catch (_) {
        return DateTime.now().add(const Duration(days: 7));
      }
    }

    final inviteToken = token;
    return InvitationModel(
      token: inviteToken,
      listId: data['listId'] as String? ?? '',
      listName: data['listName'] as String? ?? 'Shared Shopping List',
      invitedByUid: data['invitedByUid'] as String? ?? '',
      invitedByName: data['invitedByName'] as String? ?? 'Household Member',
      role: data['role'] as String? ?? 'editor',
      maxUses: (data['maxUses'] as num?)?.toInt() ?? 5,
      usedCount: (data['usedCount'] as num?)?.toInt() ?? 0,
      expiresAt: parseDate(data['expiresAt']),
      inviteUrl: data['inviteUrl'] as String? ??
          'https://shoppinglist.page.link/join?token=$inviteToken',
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'token': token,
      'listId': listId,
      'listName': listName,
      'invitedByUid': invitedByUid,
      'invitedByName': invitedByName,
      'role': role,
      'maxUses': maxUses,
      'usedCount': usedCount,
      'expiresAt': expiresAt.toIso8601String(),
      'inviteUrl': inviteUrl,
    };
  }
}
