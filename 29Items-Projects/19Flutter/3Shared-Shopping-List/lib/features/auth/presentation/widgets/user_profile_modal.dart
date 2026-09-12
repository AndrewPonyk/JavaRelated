import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../bloc/auth_bloc.dart';
import '../bloc/auth_event.dart';
import '../bloc/auth_state.dart';

class UserProfileModal extends StatefulWidget {
  final AuthBloc authBloc;

  const UserProfileModal({super.key, required this.authBloc});

  @override
  State<UserProfileModal> createState() => _UserProfileModalState();
}

class _UserProfileModalState extends State<UserProfileModal> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _nameController = TextEditingController();
  bool _isLinking = false;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<AuthState>(
      stream: widget.authBloc.stream,
      initialData: widget.authBloc.state,
      builder: (context, snapshot) {
        final state = snapshot.data ?? const AuthState();
        final user = state.user;

        return Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(context).viewInsets.bottom + 24,
            top: 24,
            left: 20,
            right: 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    backgroundColor: user?.isAnonymous == false
                        ? Colors.black87
                        : AppColors.primaryContainer,
                    child: Icon(
                      user?.isAnonymous == false ? Icons.code : Icons.person,
                      color: user?.isAnonymous == false
                          ? Colors.white
                          : AppColors.primary,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user?.displayName ??
                              (user?.isAnonymous == false
                                  ? 'GitHub User'
                                  : 'Household Shopper'),
                          style: const TextStyle(
                              fontSize: 16, fontWeight: FontWeight.bold),
                        ),
                        Text(
                          user?.email ??
                              (user?.isAnonymous == false
                                  ? 'GitHub Account Connected'
                                  : 'Anonymous Guest Account'),
                          style: TextStyle(
                              fontSize: 12, color: Colors.grey.shade600),
                        ),
                      ],
                    ),
                  ),
                  if (user?.isAnonymous == false)
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.green.shade50,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: Colors.green.shade200),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.check_circle,
                              size: 14, color: Colors.green),
                          SizedBox(width: 4),
                          Text(
                            'Linked',
                            style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: Colors.green),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 16),
              if (state.errorMessage != null) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  margin: const EdgeInsets.only(bottom: 12),
                  decoration: BoxDecoration(
                    color: Colors.red.shade50,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.red.shade200),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline,
                          color: Colors.red, size: 20),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          state.errorMessage!,
                          style: TextStyle(
                              color: Colors.red.shade900, fontSize: 12),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
              if (state.status == AuthStatus.authenticating) ...[
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 20),
                  child: Center(
                    child: Column(
                      children: [
                        CircularProgressIndicator(),
                        SizedBox(height: 10),
                        Text('Connecting to GitHub via Firebase...',
                            style: TextStyle(
                                fontSize: 13, color: AppColors.textSecondary)),
                      ],
                    ),
                  ),
                ),
              ] else if (user?.isAnonymous ?? true) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.secondaryContainer.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.info_outline,
                          size: 18, color: AppColors.secondary),
                      SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Link your account with email to backup lists across devices and invite roommates.',
                          style: TextStyle(fontSize: 12),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                if (_isLinking) ...[
                  TextField(
                    controller: _nameController,
                    decoration: const InputDecoration(
                        labelText: 'Display Name',
                        border: OutlineInputBorder()),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _emailController,
                    decoration: const InputDecoration(
                        labelText: 'Email Address',
                        border: OutlineInputBorder()),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _passwordController,
                    obscureText: true,
                    decoration: const InputDecoration(
                        labelText: 'Password', border: OutlineInputBorder()),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white),
                      onPressed: () {
                        if (_emailController.text.isNotEmpty &&
                            _passwordController.text.isNotEmpty) {
                          widget.authBloc.add(
                            LinkAccountEvent(
                              email: _emailController.text.trim(),
                              password: _passwordController.text,
                              displayName: _nameController.text.trim().isEmpty
                                  ? 'Shopper'
                                  : _nameController.text.trim(),
                            ),
                          );
                          Navigator.pop(context);
                        }
                      },
                      child: const Text('Save & Link Account'),
                    ),
                  ),
                ] else ...[
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.link),
                      label: const Text('Upgrade with Email / Password'),
                      onPressed: () => setState(() => _isLinking = true),
                    ),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.code, color: Colors.black),
                      label: Text(user?.isAnonymous == true
                          ? 'Link with GitHub'
                          : 'Continue with GitHub'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.black,
                        side: const BorderSide(color: Colors.black87),
                      ),
                      onPressed: () {
                        if (user?.isAnonymous == true) {
                          widget.authBloc.add(const LinkWithGithubEvent());
                        } else {
                          widget.authBloc.add(const SignInWithGithubEvent());
                        }
                      },
                    ),
                  ),
                ],
              ],
              const SizedBox(height: 8),
              if (user != null)
                SizedBox(
                  width: double.infinity,
                  child: TextButton(
                    onPressed: () {
                      widget.authBloc.add(const SignOutEvent());
                      Navigator.pop(context);
                    },
                    child: const Text('Sign Out',
                        style: TextStyle(color: AppColors.error)),
                  ),
                )
              else
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.person_outline),
                    label: const Text('Continue as Guest'),
                    onPressed: () {
                      widget.authBloc.add(const SignInAnonymouslyEvent());
                      Navigator.pop(context);
                    },
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}
