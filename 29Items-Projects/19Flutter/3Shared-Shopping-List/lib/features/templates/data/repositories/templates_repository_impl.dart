import '../../../shopping_list/domain/entities/shopping_item.dart';
import '../../../shopping_list/domain/repositories/shopping_list_repository.dart';
import '../../domain/entities/staple_template.dart';
import '../../domain/repositories/templates_repository.dart';

class TemplatesRepositoryImpl implements TemplatesRepository {
  final ShoppingListRepository _shoppingListRepository;

  final List<StapleTemplate> _predefinedTemplates = const [
    StapleTemplate(
      id: 'template-weekly',
      name: 'Weekly Staples Restock',
      description: 'Eggs, milk, sourdough, avocados, olive oil & bananas',
      iconName: 'kitchen',
      items: [
        StapleTemplateItem(
          name: 'Free Range Eggs (Dozen)',
          category: 'Dairy',
          storeSection: 'Dairy & Eggs',
          defaultQuantity: 1,
          defaultUnit: 'carton',
          estimatedPrice: 4.99,
        ),
        StapleTemplateItem(
          name: 'Oat Milk Barista',
          category: 'Dairy',
          storeSection: 'Dairy & Eggs',
          defaultQuantity: 2,
          defaultUnit: 'cartons',
          estimatedPrice: 3.80,
        ),
        StapleTemplateItem(
          name: 'Sourdough Country Loaf',
          category: 'Bakery',
          storeSection: 'Bakery & Bread',
          defaultQuantity: 1,
          defaultUnit: 'loaf',
          estimatedPrice: 5.50,
        ),
        StapleTemplateItem(
          name: 'Organic Hass Avocados',
          category: 'Produce',
          storeSection: 'Produce & Fresh Herbs',
          defaultQuantity: 4,
          defaultUnit: 'pcs',
          estimatedPrice: 1.50,
        ),
      ],
    ),
    StapleTemplate(
      id: 'template-bbq',
      name: 'Weekend BBQ & Grill',
      description: 'Burger patties, brioche buns, cheddar cheese, charcoal',
      iconName: 'outdoor_grill',
      items: [
        StapleTemplateItem(
          name: 'Grass-Fed Ground Beef',
          category: 'Meat & Fish',
          storeSection: 'Meat & Seafood',
          defaultQuantity: 2,
          defaultUnit: 'lbs',
          estimatedPrice: 8.99,
        ),
        StapleTemplateItem(
          name: 'Brioche Burger Buns',
          category: 'Bakery',
          storeSection: 'Bakery & Bread',
          defaultQuantity: 1,
          defaultUnit: 'pack',
          estimatedPrice: 4.25,
        ),
        StapleTemplateItem(
          name: 'Sharp Cheddar Slices',
          category: 'Dairy',
          storeSection: 'Dairy & Eggs',
          defaultQuantity: 1,
          defaultUnit: 'pack',
          estimatedPrice: 3.99,
        ),
      ],
    ),
  ];

  TemplatesRepositoryImpl(
      {required ShoppingListRepository shoppingListRepository})
      : _shoppingListRepository = shoppingListRepository;

  @override
  Future<List<StapleTemplate>> getTemplates() async {
    return _predefinedTemplates;
  }

  @override
  Future<void> applyTemplateToList({
    required String templateId,
    required String targetListId,
    required String userId,
  }) async {
    final template = _predefinedTemplates.firstWhere(
      (t) => t.id == templateId,
      orElse: () => _predefinedTemplates.first,
    );

    for (final tItem in template.items) {
      final shoppingItem = ShoppingItem(
        id: 'item_${DateTime.now().millisecondsSinceEpoch}_${tItem.name.hashCode.abs()}',
        listId: targetListId,
        name: tItem.name,
        category: tItem.category,
        storeSection: tItem.storeSection,
        quantity: tItem.defaultQuantity,
        unit: tItem.defaultUnit,
        priceEstimate: tItem.estimatedPrice,
        addedByUid: userId,
        hasPendingSync: true,
        updatedAt: DateTime.now(),
      );
      await _shoppingListRepository.addItem(shoppingItem);
    }
  }
}
