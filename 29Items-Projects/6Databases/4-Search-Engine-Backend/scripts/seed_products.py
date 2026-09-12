"""Seed the catalog with a realistic demo dataset (~1,000 products).

Writes PostgreSQL (source of truth) and bulk-indexes Elasticsearch directly
(bypassing the outbox — this is a bootstrap, not a live write path).
Deterministic (seeded RNG) so facet counts are reproducible across machines.

Prereqs: docker compose up, alembic upgrade head, scripts/create_indexes.py.
Usage:   python scripts/seed_products.py [--force]
"""

import argparse
import asyncio
import random
import sys
import uuid
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))

RNG = random.Random(42)

# (root, leaf, slug, path, brands, price range, nouns, feature words)
CATALOG_SPEC = [
    ("Electronics", "Headphones", "headphones", "electronics/audio/headphones",
     ["sony", "bose", "sennheiser", "jbl", "anker"], (25, 450),
     ["Wireless Headphones", "Noise Canceling Headphones", "Studio Headphones", "Earbuds"],
     ["Bluetooth 5.3", "ANC", "40h battery", "Hi-Res audio"]),
    ("Electronics", "Speakers", "speakers", "electronics/audio/speakers",
     ["sony", "jbl", "sonos", "marshall", "bose"], (30, 900),
     ["Portable Speaker", "Bookshelf Speaker", "Soundbar", "Smart Speaker"],
     ["360° sound", "IP67 waterproof", "multi-room", "deep bass"]),
    ("Electronics", "Laptops", "laptops", "electronics/computers/laptops",
     ["apple", "lenovo", "dell", "asus", "hp"], (450, 3200),
     ["Laptop", "Ultrabook", "Gaming Laptop", "Notebook"],
     ["16GB RAM", "1TB SSD", "OLED display", "all-day battery"]),
    ("Electronics", "Televisions", "tvs", "electronics/video/tvs",
     ["sony", "lg", "samsung", "tcl", "philips"], (250, 3500),
     ["4K OLED Television", "QLED TV", "Smart TV", "Mini-LED Television"],
     ["120Hz", "Dolby Vision", "HDMI 2.1", "voice control"]),
    ("Electronics", "Smartphones", "smartphones", "electronics/mobile/smartphones",
     ["apple", "samsung", "google", "xiaomi", "nothing"], (180, 1600),
     ["Smartphone", "5G Smartphone", "Foldable Phone"],
     ["OIS camera", "120Hz AMOLED", "fast charging", "IP68"]),
    ("Home", "Refrigerators", "fridges", "home/kitchen/fridges",
     ["lg", "samsung", "bosch", "whirlpool", "beko"], (400, 2800),
     ["Refrigerator", "French Door Fridge", "Side-by-Side Fridge"],
     ["No-Frost", "inverter compressor", "A++ efficiency", "ice maker"]),
    ("Home", "Sofas", "sofas", "home/furniture/sofas",
     ["ikea", "westelm", "articulate", "burrow", "sactional"], (350, 2600),
     ["Sofa", "Sectional Couch", "Sleeper Sofa", "Loveseat"],
     ["stain-resistant fabric", "modular", "memory foam", "oak legs"]),
    ("Fashion", "Sneakers", "sneakers", "fashion/shoes/sneakers",
     ["nike", "adidas", "newbalance", "puma", "asics"], (45, 240),
     ["Running Shoes", "Sneakers", "Trail Running Shoes", "Trainers"],
     ["breathable mesh", "carbon plate", "cushioned sole", "recycled materials"]),
]

LINES = ["Aero", "Pulse", "Vertex", "Nova", "Prime", "Flux", "Echo", "Titan"]
COLORS = ["black", "white", "silver", "blue", "red", "graphite", "sand"]
PRODUCTS_PER_LEAF = 125  # 8 leaves × 125 = 1000 products


def build_rows():  # type: ignore[no-untyped-def]
    """Deterministic (categories, products) object graphs, ids preassigned."""
    from app.db.models import Category, Product

    categories: list[Category] = []
    products: list[Product] = []
    roots: dict[str, Category] = {}
    now = datetime.now(UTC)

    for root_name, leaf_name, slug, path, brands, price_range, nouns, feats in CATALOG_SPEC:
        root = roots.get(root_name)
        if root is None:
            root = Category(
                id=uuid.uuid4(), name=root_name, slug=root_name.lower(), path=root_name.lower()
            )
            roots[root_name] = root
            categories.append(root)
        leaf = Category(id=uuid.uuid4(), name=leaf_name, slug=slug, parent_id=root.id, path=path)
        categories.append(leaf)

        low, high = price_range
        for i in range(PRODUCTS_PER_LEAF):
            brand = RNG.choice(brands)
            noun = RNG.choice(nouns)
            line = RNG.choice(LINES)
            model = f"{RNG.choice('ABCDEFG')}{RNG.randint(10, 99)}"
            color = RNG.choice(COLORS)
            name = f"{brand.title()} {line} {model} {noun}"
            price = Decimal(str(round(RNG.uniform(low, high), 2)))
            popularity = round(RNG.expovariate(1 / 20) + 1, 1)  # long-tail, min 1
            created = now - timedelta(days=RNG.randint(0, 365))
            product = Product(
                id=uuid.uuid4(),
                sku=f"{brand[:4].upper()}-{slug[:4].upper()}-{i:04d}",
                name=name,
                description=(
                    f"{name} with {feats[i % len(feats)]} and "
                    f"{feats[(i + 1) % len(feats)]}. Available in {color}."
                ),
                brand=brand,
                category_id=leaf.id,
                price=price,
                attributes={"color": color, "line": line, "model": model},
                popularity=popularity,
                in_stock=RNG.random() > 0.08,
                is_active=True,
                created_at=created,
                updated_at=created,
            )
            product.category = leaf  # avoid lazy-load during ES doc building
            products.append(product)
    return categories, products


async def main(force: bool) -> None:
    from app.core.config import get_settings
    from app.db.models import Category, IndexOutbox, Product, SearchEvent
    from app.db.session import async_session_factory
    from app.search.es_client import create_es_client
    from app.services.indexing_service import IndexingService
    from sqlalchemy import delete, func, select

    settings = get_settings()
    async with async_session_factory() as session:
        existing = await session.scalar(select(func.count()).select_from(Product)) or 0
        if existing and not force:
            sys.exit(
                f"Catalog already has {existing} products. Re-run with --force to wipe & reseed."
            )
        if existing and force:
            print(f"--force: removing {existing} existing products")
            for model in (SearchEvent, IndexOutbox, Product, Category):
                await session.execute(delete(model))
            await session.commit()

        categories, products = build_rows()
        session.add_all(categories)
        await session.flush()
        session.add_all(products)
        await session.commit()
        print(f"PostgreSQL: inserted {len(categories)} categories, {len(products)} products")

    es = create_es_client(settings)
    try:
        if force:
            # PG was wiped above; drop the old documents too or the index keeps
            # ghosts of products that no longer exist in the source of truth.
            await es.options(ignore_status=404).delete_by_query(
                index=settings.es_products_alias,
                query={"match_all": {}},
                conflicts="proceed",
                refresh=True,
            )
            print("Elasticsearch: cleared existing documents")
        indexing = IndexingService(es=es, settings=settings)
        indexed, failed = await indexing.bulk_index(products)
        await es.indices.refresh(index=settings.es_products_alias)
        print(f"Elasticsearch: indexed {indexed} documents ({failed} failed)")
    finally:
        await es.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true", help="wipe existing catalog first")
    args = parser.parse_args()
    asyncio.run(main(force=args.force))
