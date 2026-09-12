import 'package:firebase_auth/firebase_auth.dart' as fb;
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/data/repositories/auth_repository_impl.dart';
import 'features/auth/presentation/bloc/auth_bloc.dart';
import 'features/collaboration/data/repositories/invitation_repository_impl.dart';
import 'features/collaboration/presentation/bloc/invitation_bloc.dart';
import 'features/shopping_list/data/repositories/shopping_list_repository_impl.dart';
import 'features/shopping_list/presentation/screens/shopping_list_screen.dart';
import 'features/templates/data/repositories/templates_repository_impl.dart';
import 'features/templates/presentation/bloc/templates_bloc.dart';
import 'firebase_options.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  fb.FirebaseAuth? firebaseAuth;
  try {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
    firebaseAuth = fb.FirebaseAuth.instance;
  } catch (e) {
    debugPrint('Firebase initialization notice: $e');
  }

  // Initialize concrete repositories
  final shoppingRepository = ShoppingListRepositoryImpl();
  final authRepository = AuthRepositoryImpl(firebaseAuth: firebaseAuth);
  final invitationRepository = InvitationRepositoryImpl();
  final templatesRepository =
      TemplatesRepositoryImpl(shoppingListRepository: shoppingRepository);

  // Initialize state BLoCs
  final authBloc = AuthBloc(repository: authRepository);
  final invitationBloc = InvitationBloc(repository: invitationRepository);
  final templatesBloc = TemplatesBloc(repository: templatesRepository);

  runApp(
    SharedShoppingListApp(
      shoppingRepository: shoppingRepository,
      authBloc: authBloc,
      invitationBloc: invitationBloc,
      templatesBloc: templatesBloc,
    ),
  );
}

class SharedShoppingListApp extends StatelessWidget {
  final ShoppingListRepositoryImpl shoppingRepository;
  final AuthBloc authBloc;
  final InvitationBloc invitationBloc;
  final TemplatesBloc templatesBloc;

  const SharedShoppingListApp({
    super.key,
    required this.shoppingRepository,
    required this.authBloc,
    required this.invitationBloc,
    required this.templatesBloc,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Shared Shopping List',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: ShoppingListScreen(
        listId: 'list-001',
        repository: shoppingRepository,
        authBloc: authBloc,
        invitationBloc: invitationBloc,
        templatesBloc: templatesBloc,
      ),
    );
  }
}
