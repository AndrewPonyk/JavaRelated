abstract class InvitationEvent {
  const InvitationEvent();
}

class CreateInviteEvent extends InvitationEvent {
  final String listId;
  final String role;
  const CreateInviteEvent({required this.listId, this.role = 'editor'});
}

class RedeemInviteEvent extends InvitationEvent {
  final String token;
  const RedeemInviteEvent(this.token);
}

class LoadMembersEvent extends InvitationEvent {
  final String listId;
  const LoadMembersEvent(this.listId);
}
