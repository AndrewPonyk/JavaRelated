import 'package:flutter_test/flutter_test.dart';
import 'package:shared_shopping_list/features/auth/data/repositories/auth_repository_impl.dart';
import 'package:shared_shopping_list/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:shared_shopping_list/features/collaboration/data/repositories/invitation_repository_impl.dart';
import 'package:shared_shopping_list/features/collaboration/presentation/bloc/invitation_bloc.dart';
import 'package:shared_shopping_list/features/shopping_list/data/repositories/shopping_list_repository_impl.dart';
import 'package:shared_shopping_list/features/templates/data/repositories/templates_repository_impl.dart';
import 'package:shared_shopping_list/features/templates/presentation/bloc/templates_bloc.dart';
import 'package:shared_shopping_list/main.dart';

void main() {
  testWidgets('App launches and renders shared shopping list screen',
      (WidgetTester tester) async {
    final shoppingRepository = ShoppingListRepositoryImpl();
    final authRepository = AuthRepositoryImpl();
    final invitationRepository = InvitationRepositoryImpl();
    final templatesRepository =
        TemplatesRepositoryImpl(shoppingListRepository: shoppingRepository);

    final authBloc = AuthBloc(repository: authRepository);
    final invitationBloc = InvitationBloc(repository: invitationRepository);
    final templatesBloc = TemplatesBloc(repository: templatesRepository);

    await tester.pumpWidget(
      SharedShoppingListApp(
        shoppingRepository: shoppingRepository,
        authBloc: authBloc,
        invitationBloc: invitationBloc,
        templatesBloc: templatesBloc,
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    // Verify app title, categories, and initial items are rendered
    expect(find.text('Family Grocery Run'), findsOneWidget);
    expect(find.text('All Items'), findsOneWidget);
    expect(find.text('Organic Avocados'), findsOneWidget);
    expect(find.text('Sourdough Boule'), findsOneWidget);
  });
}
