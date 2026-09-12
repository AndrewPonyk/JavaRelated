abstract class AuthEvent {
  const AuthEvent();
}

class CheckAuthStatusEvent extends AuthEvent {
  const CheckAuthStatusEvent();
}

class SignInAnonymouslyEvent extends AuthEvent {
  const SignInAnonymouslyEvent();
}

class LinkAccountEvent extends AuthEvent {
  final String email;
  final String password;
  final String displayName;

  const LinkAccountEvent({
    required this.email,
    required this.password,
    required this.displayName,
  });
}

class SignInWithGithubEvent extends AuthEvent {
  const SignInWithGithubEvent();
}

class LinkWithGithubEvent extends AuthEvent {
  const LinkWithGithubEvent();
}

class SignOutEvent extends AuthEvent {
  const SignOutEvent();
}
