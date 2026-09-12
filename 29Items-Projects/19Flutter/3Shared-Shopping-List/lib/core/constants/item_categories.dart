import 'package:flutter/material.dart';

/// Predefined grocery item categories with iconography and color accents
class ItemCategoryInfo {
  final String name;
  final IconData icon;
  final Color color;

  const ItemCategoryInfo({
    required this.name,
    required this.icon,
    required this.color,
  });
}

class ItemCategories {
  static const String all = 'All Items';
  static const String produce = 'Produce';
  static const String dairy = 'Dairy';
  static const String bakery = 'Bakery';
  static const String meat = 'Meat & Fish';
  static const String pantry = 'Pantry';
  static const String snacks = 'Snacks & Drinks';
  static const String frozen = 'Frozen';
  static const String household = 'Household';

  static const List<ItemCategoryInfo> categories = [
    ItemCategoryInfo(name: produce, icon: Icons.eco, color: Color(0xFF2E7D32)),
    ItemCategoryInfo(
        name: dairy, icon: Icons.egg_alt_outlined, color: Color(0xFF0288D1)),
    ItemCategoryInfo(
        name: bakery,
        icon: Icons.bakery_dining_outlined,
        color: Color(0xFFE65100)),
    ItemCategoryInfo(
        name: meat, icon: Icons.set_meal_outlined, color: Color(0xFFC2185B)),
    ItemCategoryInfo(
        name: pantry, icon: Icons.kitchen_outlined, color: Color(0xFF6D4C41)),
    ItemCategoryInfo(
        name: snacks,
        icon: Icons.local_cafe_outlined,
        color: Color(0xFFF57C00)),
    ItemCategoryInfo(
        name: frozen, icon: Icons.ac_unit, color: Color(0xFF0097A7)),
    ItemCategoryInfo(
        name: household,
        icon: Icons.cleaning_services_outlined,
        color: Color(0xFF5E35B1)),
  ];

  static ItemCategoryInfo getInfo(String categoryName) {
    return categories.firstWhere(
      (c) => c.name.toLowerCase() == categoryName.toLowerCase(),
      orElse: () => const ItemCategoryInfo(
        name: 'General',
        icon: Icons.shopping_basket_outlined,
        color: Color(0xFF546E7A),
      ),
    );
  }
}
