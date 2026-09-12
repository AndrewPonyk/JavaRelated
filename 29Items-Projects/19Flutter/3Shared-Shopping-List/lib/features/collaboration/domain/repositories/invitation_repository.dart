import '../entities/list_invitation.dart';

abstract class InvitationRepository {
  /// Generates a new share invitation token and URL for a list
  Future<ListInvitation> createInvitation({
    required String listId,
    required String role,
    int expiresInDays = 7,
  });

  /// Redeems an invitation token and adds the current user to memberUids
  Future<String> redeemInvitation(String token);

  /// Retrieves list members display information
  Future<List<Map<String, String>>> getListMembers(String listId);
}
