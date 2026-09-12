# Database Schema & Data Models: Cloud Firestore

This document defines the schema, collection hierarchy, composite indexes, and data relationships for the **Shared Shopping List** application.

---

## 1. Firestore Collection Hierarchy

```text
/users/{userId}                              # User profile & household membership
/shopping_lists/{listId}                     # Shopping list metadata & access list
    /items/{itemId}                          # Subcollection: Individual grocery items
/invitations/{invitationToken}               # Shareable invitation tokens (TTL-backed)
/staple_templates/{templateId}               # Predefined staple templates (Weekly Staples, BBQ)
```

---

## 2. Collection Schemas

### 2.1 `/users/{userId}`
Represents authenticated or anonymous user accounts.

```typescript
interface UserDocument {
  uid: string;                       // Firebase Auth UID
  displayName: string | null;        // e.g. "Alex Miller"
  email: string | null;              // e.g. "alex@example.com" (null for anonymous)
  isAnonymous: boolean;              // true if unlinked guest
  activeListId: string | null;       // ID of currently selected list
  fcmTokens: string[];               // FCM device tokens for push notifications
  createdAt: Timestamp;              // Server timestamp
  updatedAt: Timestamp;
}
```

### 2.2 `/shopping_lists/{listId}`
Monitors collaborative access, household ownership, and aggregate statistics.

```typescript
interface ShoppingListDocument {
  id: string;                        // Unique UUID / Firestore ID
  name: string;                      // e.g. "Weekly Family Groceries"
  ownerUid: string;                  // UID of creator / admin
  memberUids: string[];              // UIDs with read/write access (O(1) rule query)
  memberRoles: {                     // Role mapping: 'owner' | 'editor' | 'viewer'
    [uid: string]: 'owner' | 'editor' | 'viewer';
  };
  totalEstimatedPrice: number;       // Running total estimate in base currency
  totalItemsCount: number;           // Total active item count
  gotItItemsCount: number;           // Checked off item count
  createdAt: Timestamp;
  updatedAt: Timestamp;
}
```

### 2.3 Subcollection: `/shopping_lists/{listId}/items/{itemId}`
Atomic grocery items belonging to a specific list. Modeled as a subcollection to isolate concurrent writes.

```typescript
interface ShoppingItemDocument {
  id: string;                        // Unique Item UUID
  listId: string;                    // Foreign Key to parent ShoppingList
  name: string;                      // e.g. "Organic Whole Milk"
  category: string;                  // e.g. "Dairy", "Produce", "Bakery"
  storeSection: string;              // e.g. "Aisle 3 - Dairy Refrigerated"
  storeSectionOrder: number;         // Integer sort key representing walking order (1 = Entrance, 10 = Checkout)
  quantity: number;                  // e.g. 2
  unit: string;                      // e.g. "gallons", "lbs", "units"
  priceEstimate: number;             // Estimated price per unit
  isGotIt: boolean;                  // Checked off ("Got It") status
  isFavorite: boolean;               // Pinned as favorite for quick-add
  isArchived: boolean;               // Soft-delete flag (excludes from active real-time queries)
  addedByUid: string;                // UID of user who created the item
  gotItByUid: string | null;         // UID of shopper who checked it off
  createdAt: Timestamp;
  updatedAt: Timestamp;
}
```

### 2.4 `/invitations/{invitationToken}`
Time-limited, cryptographically secure sharing tokens for household collaboration.

```typescript
interface InvitationDocument {
  token: string;                     // 32-character random invite token
  listId: string;                    // Target list ID
  listName: string;                  // Cached name for preview
  invitedByUid: string;              // Sender UID
  invitedByName: string;             // Display name of inviter
  role: 'editor' | 'viewer';         // Assigned role upon acceptance
  maxUses: number;                   // e.g. 1 (single-use) or 5 (family pool)
  usedCount: number;                 // Incremented on redemption
  expiresAt: Timestamp;              // TTL expiration timestamp
  createdAt: Timestamp;
}
```

---

## 3. Composite Indexes (`firestore.indexes.json`)

To enable lightning-fast real-time grocery aisle sorting without client-side lag:

1. **Active Items by Store Route Index**:
   - Collection: `items`
   - Fields:
     - `isArchived` ASCENDING
     - `storeSectionOrder` ASCENDING
     - `updatedAt` DESCENDING
2. **Category Grouping Index**:
   - Collection: `items`
   - Fields:
     - `isArchived` ASCENDING
     - `category` ASCENDING
     - `isGotIt` ASCENDING
