import 'package:flutter_test/flutter_test.dart';
import 'package:shared_shopping_list/features/shopping_list/data/repositories/shopping_list_repository_impl.dart';
import 'package:shared_shopping_list/features/templates/data/repositories/templates_repository_impl.dart';
import 'package:shared_shopping_list/features/templates/presentation/bloc/templates_bloc.dart';
import 'package:shared_shopping_list/features/templates/presentation/bloc/templates_event.dart';
import 'package:shared_shopping_list/features/templates/presentation/bloc/templates_state.dart';

void main() {
  group('TemplatesBloc', () {
    late ShoppingListRepositoryImpl shoppingRepo;
    late TemplatesRepositoryImpl templatesRepo;
    late TemplatesBloc templatesBloc;

    setUp(() {
      shoppingRepo = ShoppingListRepositoryImpl();
      templatesRepo =
          TemplatesRepositoryImpl(shoppingListRepository: shoppingRepo);
      templatesBloc = TemplatesBloc(repository: templatesRepo);
    });

    tearDown(() {
      templatesBloc.close();
    });

    test('LoadTemplatesEvent loads weekly staple presets', () async {
      templatesBloc.add(const LoadTemplatesEvent());
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(templatesBloc.state.status, TemplatesStatus.loaded);
      expect(templatesBloc.state.templates, isNotEmpty);
      expect(
          templatesBloc.state.templates.any((t) => t.id == 'template-weekly'),
          isTrue);
    });

    test('ApplyTemplateEvent adds all items from template to shopping list',
        () async {
      templatesBloc.add(const LoadTemplatesEvent());
      await Future<void>.delayed(const Duration(milliseconds: 50));

      templatesBloc.add(const ApplyTemplateEvent(
        templateId: 'template-weekly',
        targetListId: 'list-001',
        userId: 'user-001',
      ));
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(templatesBloc.state.status, TemplatesStatus.applied);
      expect(templatesBloc.state.lastAppliedTemplateName,
          'Weekly Staples Restock');
    });
  });
}
