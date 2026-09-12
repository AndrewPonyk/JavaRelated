class StapleTemplateItem {
  final String name;
  final String category;
  final String storeSection;
  final double defaultQuantity;
  final String defaultUnit;
  final double estimatedPrice;

  const StapleTemplateItem({
    required this.name,
    required this.category,
    required this.storeSection,
    required this.defaultQuantity,
    required this.defaultUnit,
    required this.estimatedPrice,
  });
}

class StapleTemplate {
  final String id;
  final String name;
  final String description;
  final String iconName;
  final List<StapleTemplateItem> items;

  const StapleTemplate({
    required this.id,
    required this.name,
    required this.description,
    required this.iconName,
    required this.items,
  });
}
