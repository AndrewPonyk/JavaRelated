import 'dart:async';
import '../../domain/repositories/invitation_repository.dart';
import 'invitation_event.dart';
import 'invitation_state.dart';

class InvitationBloc {
  final InvitationRepository _repository;
  InvitationState _state = const InvitationState();
  final _stateController = StreamController<InvitationState>.broadcast();

  InvitationBloc({required InvitationRepository repository})
      : _repository = repository;

  InvitationState get state => _state;
  Stream<InvitationState> get stream => _stateController.stream;

  void emit(InvitationState newState) {
    _state = newState;
    if (!_stateController.isClosed) {
      _stateController.add(newState);
    }
  }

  void add(InvitationEvent event) {
    if (event is CreateInviteEvent) {
      _onCreateInvite(event);
    } else if (event is RedeemInviteEvent) {
      _onRedeemInvite(event);
    } else if (event is LoadMembersEvent) {
      _onLoadMembers(event);
    }
  }

  Future<void> _onCreateInvite(CreateInviteEvent event) async {
    emit(state.copyWith(status: InvitationStatus.loading));
    try {
      final invite = await _repository.createInvitation(
        listId: event.listId,
        role: event.role,
      );
      emit(state.copyWith(
        status: InvitationStatus.generated,
        currentInvite: invite,
      ));
    } catch (e) {
      emit(state.copyWith(
        status: InvitationStatus.failure,
        errorMessage: e.toString(),
      ));
    }
  }

  Future<void> _onRedeemInvite(RedeemInviteEvent event) async {
    emit(state.copyWith(status: InvitationStatus.loading));
    try {
      final listId = await _repository.redeemInvitation(event.token);
      emit(state.copyWith(
        status: InvitationStatus.redeemed,
        redeemedListId: listId,
      ));
    } catch (e) {
      emit(state.copyWith(
        status: InvitationStatus.failure,
        errorMessage: e.toString(),
      ));
    }
  }

  Future<void> _onLoadMembers(LoadMembersEvent event) async {
    try {
      final members = await _repository.getListMembers(event.listId);
      emit(state.copyWith(members: members));
    } catch (_) {}
  }

  void close() {
    _stateController.close();
  }
}
