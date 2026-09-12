/// Immutable Domain Entity representing an individual grocery item
class ShoppingItem {
  final String id;
  final String listId;
  final String name;
  final String category;
  final String storeSection;
  final int storeSectionOrder;
  final double quantity;
  final String unit;
  final double priceEstimate;
  final bool isGotIt;
  final bool isFavorite;
  final bool isArchived;
  final String addedByUid;
  final String? gotItByUid;
  final bool hasPendingSync; // Client-side indicator for offline queued writes
  final DateTime updatedAt;

  const ShoppingItem({
    required this.id,
    required this.listId,
    required this.name,
    this.category = 'General',
    this.storeSection = 'Produce & Fresh Herbs',
    this.storeSectionOrder = 0,
    this.quantity = 1.0,
    this.unit = 'pcs',
    this.priceEstimate = 0.0,
    this.isGotIt = false,
    this.isFavorite = false,
    this.isArchived = false,
    required this.addedByUid,
    this.gotItByUid,
    this.hasPendingSync = false,
    required this.updatedAt,
  });

  ShoppingItem copyWith({
    String? id,
    String? listId,
    String? name,
    String? category,
    String? storeSection,
    int? storeSectionOrder,
    double? quantity,
    String? unit,
    double? priceEstimate,
    bool? isGotIt,
    bool? isFavorite,
    bool? isArchived,
    String? addedByUid,
    String? gotItByUid,
    bool? hasPendingSync,
    DateTime? updatedAt,
  }) {
    return ShoppingItem(
      id: id ?? this.id,
      listId: listId ?? this.listId,
      name: name ?? this.name,
      category: category ?? this.category,
      storeSection: storeSection ?? this.storeSection,
      storeSectionOrder: storeSectionOrder ?? this.storeSectionOrder,
      quantity: quantity ?? this.quantity,
      unit: unit ?? this.unit,
      priceEstimate: priceEstimate ?? this.priceEstimate,
      isGotIt: isGotIt ?? this.isGotIt,
      isFavorite: isFavorite ?? this.isFavorite,
      isArchived: isArchived ?? this.isArchived,
      addedByUid: addedByUid ?? this.addedByUid,
      gotItByUid: gotItByUid ?? this.gotItByUid,
      hasPendingSync: hasPendingSync ?? this.hasPendingSync,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ShoppingItem &&
          runtimeType == other.runtimeType &&
          id == other.id &&
          isGotIt == other.isGotIt &&
          quantity == other.quantity &&
          hasPendingSync == other.hasPendingSync;

  @override
  int get hashCode =>
      id.hashCode ^
      isGotIt.hashCode ^
      quantity.hashCode ^
      hasPendingSync.hashCode;
}
