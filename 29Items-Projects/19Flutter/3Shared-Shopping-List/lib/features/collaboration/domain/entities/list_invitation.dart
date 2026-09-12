/// Domain entity representing a shareable list invitation
class ListInvitation {
  final String token;
  final String listId;
  final String listName;
  final String invitedByUid;
  final String invitedByName;
  final String role; // 'editor' | 'viewer'
  final int maxUses;
  final int usedCount;
  final DateTime expiresAt;
  final String inviteUrl;

  const ListInvitation({
    required this.token,
    required this.listId,
    required this.listName,
    required this.invitedByUid,
    required this.invitedByName,
    this.role = 'editor',
    this.maxUses = 5,
    this.usedCount = 0,
    required this.expiresAt,
    required this.inviteUrl,
  });

  bool get isExpired => DateTime.now().isAfter(expiresAt);
  bool get isExhausted => usedCount >= maxUses;
  bool get isValid => !isExpired && !isExhausted;
}
