/// Predefined store aisle layout to optimize walking routes during shopping trips
class StoreSections {
  static const String entranceProduce = 'Produce & Fresh Herbs';
  static const String bakery = 'Bakery & Bread';
  static const String deli = 'Deli & Prepared Foods';
  static const String meatSeafood = 'Meat & Seafood';
  static const String dairy = 'Dairy & Eggs';
  static const String pantryDryGoods = 'Pantry & Canned Goods';
  static const String snacksBeverages = 'Snacks & Beverages';
  static const String frozen = 'Frozen Foods';
  static const String household = 'Household & Cleaning';
  static const String checkout = 'Checkout & Register Staples';

  /// Sequential walking order through standard supermarket aisles
  static const List<String> walkingOrder = [
    entranceProduce,
    bakery,
    deli,
    meatSeafood,
    dairy,
    pantryDryGoods,
    snacksBeverages,
    frozen,
    household,
    checkout,
  ];

  /// Returns numeric sort index for route optimization
  static int getOrderIndex(String section) {
    final index = walkingOrder.indexOf(section);
    return index != -1 ? index : 999;
  }
}
