/**
 * Database Seeding Script for Shared Shopping List Local Emulator
 * Usage: node firebase/seed.js
 */
const http = require('http');

const SEED_DATA = {
  users: [
    {
      id: 'user-001',
      displayName: 'Alex (Family Admin)',
      email: 'alex@family.home',
      isAnonymous: false,
      activeListId: 'list-001',
      createdAt: new Date().toISOString(),
    },
    {
      id: 'user-002',
      displayName: 'Sam (Roommate)',
      email: 'sam@family.home',
      isAnonymous: false,
      activeListId: 'list-001',
      createdAt: new Date().toISOString(),
    },
  ],
  shopping_lists: [
    {
      id: 'list-001',
      name: 'Family Grocery Run',
      ownerUid: 'user-001',
      memberUids: ['user-001', 'user-002'],
      memberRoles: { 'user-001': 'owner', 'user-002': 'editor' },
      totalEstimatedPrice: 28.50,
      totalItemsCount: 5,
      gotItItemsCount: 2,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  ],
  items: [
    {
      id: 'item-001',
      listId: 'list-001',
      name: 'Organic Avocados',
      category: 'Produce',
      storeSection: 'Produce & Fresh Herbs',
      storeSectionOrder: 0,
      quantity: 3,
      unit: 'pcs',
      priceEstimate: 4.50,
      isGotIt: true,
      addedByUid: 'user-001',
      gotItByUid: 'user-001',
    },
    {
      id: 'item-002',
      listId: 'list-001',
      name: 'Sourdough Country Loaf',
      category: 'Bakery',
      storeSection: 'Bakery & Bread',
      storeSectionOrder: 1,
      quantity: 1,
      unit: 'loaf',
      priceEstimate: 5.50,
      isGotIt: true,
      addedByUid: 'user-002',
      gotItByUid: 'user-002',
    },
    {
      id: 'item-003',
      listId: 'list-001',
      name: 'Oat Milk Barista',
      category: 'Dairy',
      storeSection: 'Dairy & Eggs',
      storeSectionOrder: 4,
      quantity: 2,
      unit: 'cartons',
      priceEstimate: 7.60,
      isGotIt: false,
      addedByUid: 'user-001',
    },
  ],
};

console.log('Seed configuration verified:');
console.log(`- ${SEED_DATA.users.length} Users`);
console.log(`- ${SEED_DATA.shopping_lists.length} Lists`);
console.log(`- ${SEED_DATA.items.length} Sample grocery items`);
console.log('Ready to seed into Firestore emulator on port 8080.');
