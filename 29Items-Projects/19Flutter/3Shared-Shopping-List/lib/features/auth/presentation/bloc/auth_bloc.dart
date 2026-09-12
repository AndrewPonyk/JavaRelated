import 'dart:async';
import '../../domain/repositories/auth_repository.dart';
import 'auth_event.dart';
import 'auth_state.dart';

class AuthBloc {
  final AuthRepository _repository;
  StreamSubscription<dynamic>? _authSubscription;

  AuthState _state = const AuthState();
  final _stateController = StreamController<AuthState>.broadcast();

  AuthBloc({required AuthRepository repository}) : _repository = repository {
    _authSubscription = _repository.authStateChanges.listen((user) {
      if (user != null) {
        emit(AuthState(status: AuthStatus.authenticated, user: user));
      } else {
        emit(const AuthState(status: AuthStatus.unauthenticated));
      }
    });
  }

  AuthState get state => _state;
  Stream<AuthState> get stream => _stateController.stream;

  void emit(AuthState newState) {
    _state = newState;
    if (!_stateController.isClosed) {
      _stateController.add(newState);
    }
  }

  void add(AuthEvent event) {
    if (event is CheckAuthStatusEvent) {
      _onCheckAuthStatus();
    } else if (event is SignInAnonymouslyEvent) {
      _onSignInAnonymously();
    } else if (event is LinkAccountEvent) {
      _onLinkAccount(event);
    } else if (event is SignInWithGithubEvent) {
      _onSignInWithGithub();
    } else if (event is LinkWithGithubEvent) {
      _onLinkWithGithub();
    } else if (event is SignOutEvent) {
      _onSignOut();
    }
  }

  void _onCheckAuthStatus() {
    final user = _repository.currentUser;
    if (user != null) {
      emit(state.copyWith(status: AuthStatus.authenticated, user: user));
    } else {
      emit(state.copyWith(status: AuthStatus.unauthenticated));
    }
  }

  Future<void> _onSignInAnonymously() async {
    emit(state.copyWith(status: AuthStatus.authenticating));
    try {
      final user = await _repository.signInAnonymously();
      emit(state.copyWith(status: AuthStatus.authenticated, user: user));
    } catch (e) {
      emit(state.copyWith(
          status: AuthStatus.failure, errorMessage: e.toString()));
    }
  }

  Future<void> _onLinkAccount(LinkAccountEvent event) async {
    emit(state.copyWith(status: AuthStatus.authenticating));
    try {
      final user = await _repository.linkWithEmailPassword(
        email: event.email,
        password: event.password,
        displayName: event.displayName,
      );
      emit(state.copyWith(status: AuthStatus.authenticated, user: user));
    } catch (e) {
      emit(state.copyWith(
          status: AuthStatus.failure, errorMessage: e.toString()));
    }
  }

  Future<void> _onSignInWithGithub() async {
    emit(state.copyWith(status: AuthStatus.authenticating));
    try {
      final user = await _repository.signInWithGithub();
      emit(state.copyWith(status: AuthStatus.authenticated, user: user));
    } catch (e) {
      emit(state.copyWith(
          status: AuthStatus.failure, errorMessage: e.toString()));
    }
  }

  Future<void> _onLinkWithGithub() async {
    emit(state.copyWith(status: AuthStatus.authenticating));
    try {
      final user = await _repository.linkWithGithub();
      emit(state.copyWith(status: AuthStatus.authenticated, user: user));
    } catch (e) {
      emit(state.copyWith(
          status: AuthStatus.failure, errorMessage: e.toString()));
    }
  }

  Future<void> _onSignOut() async {
    await _repository.signOut();
    emit(const AuthState(status: AuthStatus.unauthenticated));
  }

  void close() {
    _authSubscription?.cancel();
    _stateController.close();
  }
}
