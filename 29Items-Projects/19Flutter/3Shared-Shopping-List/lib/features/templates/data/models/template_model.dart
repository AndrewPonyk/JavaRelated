import '../../domain/entities/staple_template.dart';

class TemplateModel extends StapleTemplate {
  const TemplateModel({
    required super.id,
    required super.name,
    required super.description,
    required super.iconName,
    required super.items,
  });

  factory TemplateModel.fromMap(Map<String, dynamic> data, String id) {
    final rawItems = data['items'] as List? ?? [];
    final items = rawItems.map((item) {
      final i = item as Map<String, dynamic>;
      return StapleTemplateItem(
        name: i['name'] as String? ?? '',
        category: i['category'] as String? ?? 'General',
        storeSection: i['storeSection'] as String? ?? 'Produce & Fresh Herbs',
        defaultQuantity: (i['defaultQuantity'] as num?)?.toDouble() ?? 1.0,
        defaultUnit: i['defaultUnit'] as String? ?? 'pcs',
        estimatedPrice: (i['estimatedPrice'] as num?)?.toDouble() ?? 0.0,
      );
    }).toList();

    return TemplateModel(
      id: id,
      name: data['name'] as String? ?? 'Staple Template',
      description: data['description'] as String? ?? '',
      iconName: data['iconName'] as String? ?? 'shopping_cart',
      items: items,
    );
  }
}
