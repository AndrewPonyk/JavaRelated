/// Immutable Domain Entity representing a shared shopping list
class ShoppingList {
  final String id;
  final String name;
  final String ownerUid;
  final List<String> memberUids;
  final Map<String, String> memberRoles;
  final double totalEstimatedPrice;
  final int totalItemsCount;
  final int gotItItemsCount;
  final DateTime createdAt;
  final DateTime updatedAt;

  const ShoppingList({
    required this.id,
    required this.name,
    required this.ownerUid,
    required this.memberUids,
    this.memberRoles = const {},
    this.totalEstimatedPrice = 0.0,
    this.totalItemsCount = 0,
    this.gotItItemsCount = 0,
    required this.createdAt,
    required this.updatedAt,
  });

  bool isMember(String uid) => memberUids.contains(uid);
  bool isOwner(String uid) => ownerUid == uid;

  ShoppingList copyWith({
    String? id,
    String? name,
    String? ownerUid,
    List<String>? memberUids,
    Map<String, String>? memberRoles,
    double? totalEstimatedPrice,
    int? totalItemsCount,
    int? gotItItemsCount,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return ShoppingList(
      id: id ?? this.id,
      name: name ?? this.name,
      ownerUid: ownerUid ?? this.ownerUid,
      memberUids: memberUids ?? this.memberUids,
      memberRoles: memberRoles ?? this.memberRoles,
      totalEstimatedPrice: totalEstimatedPrice ?? this.totalEstimatedPrice,
      totalItemsCount: totalItemsCount ?? this.totalItemsCount,
      gotItItemsCount: gotItItemsCount ?? this.gotItItemsCount,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}
