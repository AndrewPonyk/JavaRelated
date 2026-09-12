import 'package:flutter_test/flutter_test.dart';
import 'package:shared_shopping_list/features/auth/data/repositories/auth_repository_impl.dart';
import 'package:shared_shopping_list/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:shared_shopping_list/features/auth/presentation/bloc/auth_event.dart';
import 'package:shared_shopping_list/features/auth/presentation/bloc/auth_state.dart';

void main() {
  group('AuthBloc', () {
    late AuthRepositoryImpl repository;
    late AuthBloc authBloc;

    setUp(() {
      repository = AuthRepositoryImpl();
      authBloc = AuthBloc(repository: repository);
    });

    tearDown(() {
      authBloc.close();
      repository.dispose();
    });

    test('initial state has unauthenticated or authenticated status', () {
      expect(authBloc.state.status,
          anyOf(AuthStatus.initial, AuthStatus.authenticated));
    });

    test('SignInAnonymouslyEvent emits authenticated with anonymous user',
        () async {
      authBloc.add(const SignInAnonymouslyEvent());
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(authBloc.state.status, AuthStatus.authenticated);
      expect(authBloc.state.user?.isAnonymous, isTrue);
    });

    test('LinkAccountEvent upgrades user to permanent email account', () async {
      authBloc.add(const LinkAccountEvent(
        email: 'alex.home@example.com',
        password: 'securePassword123',
        displayName: 'Alex Family',
      ));
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(authBloc.state.status, AuthStatus.authenticated);
      expect(authBloc.state.user?.isAnonymous, isFalse);
      expect(authBloc.state.user?.email, 'alex.home@example.com');
      expect(authBloc.state.user?.displayName, 'Alex Family');
    });

    test('SignInWithGithubEvent emits authenticated with github user',
        () async {
      authBloc.add(const SignInWithGithubEvent());
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(authBloc.state.status, AuthStatus.authenticated);
      expect(authBloc.state.user?.isAnonymous, isFalse);
      expect(authBloc.state.user?.email, 'dev@github.com');
      expect(authBloc.state.user?.displayName, 'GitHub Developer');
    });

    test('LinkWithGithubEvent links anonymous account to github', () async {
      authBloc.add(const SignInAnonymouslyEvent());
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(authBloc.state.user?.isAnonymous, isTrue);

      authBloc.add(const LinkWithGithubEvent());
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(authBloc.state.status, AuthStatus.authenticated);
      expect(authBloc.state.user?.isAnonymous, isFalse);
      expect(authBloc.state.user?.email, 'dev@github.com');
    });

    test('SignOutEvent emits unauthenticated status', () async {
      authBloc.add(const SignOutEvent());
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(authBloc.state.status, AuthStatus.unauthenticated);
      expect(authBloc.state.user, isNull);
    });
  });
}
