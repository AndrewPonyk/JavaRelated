import 'package:flutter_test/flutter_test.dart';
import 'package:shared_shopping_list/features/collaboration/data/repositories/invitation_repository_impl.dart';
import 'package:shared_shopping_list/features/collaboration/presentation/bloc/invitation_bloc.dart';
import 'package:shared_shopping_list/features/collaboration/presentation/bloc/invitation_event.dart';
import 'package:shared_shopping_list/features/collaboration/presentation/bloc/invitation_state.dart';

void main() {
  group('InvitationBloc', () {
    late InvitationRepositoryImpl repository;
    late InvitationBloc invitationBloc;

    setUp(() {
      repository = InvitationRepositoryImpl();
      invitationBloc = InvitationBloc(repository: repository);
    });

    tearDown(() {
      invitationBloc.close();
    });

    test('CreateInviteEvent creates dynamic link invitation token', () async {
      invitationBloc
          .add(const CreateInviteEvent(listId: 'list-001', role: 'editor'));
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(invitationBloc.state.status, InvitationStatus.generated);
      expect(invitationBloc.state.currentInvite, isNotNull);
      expect(invitationBloc.state.currentInvite?.role, 'editor');
      expect(invitationBloc.state.currentInvite?.inviteUrl,
          contains('https://shoppinglist.page.link'));
    });

    test('LoadMembersEvent loads current household members', () async {
      invitationBloc.add(const LoadMembersEvent('list-001'));
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(invitationBloc.state.members, isNotEmpty);
      expect(invitationBloc.state.members.first['role'], 'Owner');
    });
  });
}
