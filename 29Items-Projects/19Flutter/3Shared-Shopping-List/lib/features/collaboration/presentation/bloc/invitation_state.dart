import '../../domain/entities/list_invitation.dart';

enum InvitationStatus { initial, loading, generated, redeemed, failure }

class InvitationState {
  final InvitationStatus status;
  final ListInvitation? currentInvite;
  final String? redeemedListId;
  final List<Map<String, String>> members;
  final String? errorMessage;

  const InvitationState({
    this.status = InvitationStatus.initial,
    this.currentInvite,
    this.redeemedListId,
    this.members = const [],
    this.errorMessage,
  });

  InvitationState copyWith({
    InvitationStatus? status,
    ListInvitation? currentInvite,
    String? redeemedListId,
    List<Map<String, String>>? members,
    String? errorMessage,
  }) {
    return InvitationState(
      status: status ?? this.status,
      currentInvite: currentInvite ?? this.currentInvite,
      redeemedListId: redeemedListId ?? this.redeemedListId,
      members: members ?? this.members,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is InvitationState &&
          runtimeType == other.runtimeType &&
          status == other.status &&
          currentInvite?.token == other.currentInvite?.token &&
          redeemedListId == other.redeemedListId &&
          members.length == other.members.length;

  @override
  int get hashCode =>
      status.hashCode ^
      currentInvite.hashCode ^
      redeemedListId.hashCode ^
      members.length.hashCode;
}
