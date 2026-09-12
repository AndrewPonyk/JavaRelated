// Mounted into the MongoDB container's docker-entrypoint-initdb.d so the local
// catalog has data on first boot. Production seeding is done via a migration job.
db = db.getSiblingDB('catalog');

db.createCollection('products');
db.products.createIndex({ category: 1, active: 1 });
db.products.createIndex({ sku: 1 }, { unique: true });

db.products.insertMany([
  {
    _id: 'p-1001',
    sku: 'LAP-XPS-13',
    name: 'Ultrabook XPS 13',
    description: 'Lightweight 13-inch developer laptop.',
    category: 'laptops',
    price: 1299.00,
    currency: 'EUR',
    active: true,
    stockOnHand: 42,
    attributes: { ram: '16GB', cpu: 'i7', storage: '512GB SSD' },
    variants: [],
    updatedAt: new Date()
  },
  {
    _id: 'p-2001',
    sku: 'HDPH-QC-35',
    name: 'QuietComfort 35 Headphones',
    description: 'Noise-cancelling over-ear headphones.',
    category: 'audio',
    price: 299.00,
    currency: 'EUR',
    active: true,
    stockOnHand: 110,
    attributes: { color: 'black', wireless: true },
    variants: [
      { variantSku: 'HDPH-QC-35-SLV', options: { color: 'silver' }, priceDelta: 0, stock: 25 }
    ],
    updatedAt: new Date()
  }
]);

print('Seeded ' + db.products.countDocuments() + ' products.');
