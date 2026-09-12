"""Zero-downtime full reindex (mapping/analyzer changes, disaster recovery).

    products (alias) ── vN ──> create v(N+1) ──> bulk from PG ──> verify counts
        ──> atomic alias swap ──> catch-up (writes made during the bulk) ──> drop vN

The catch-up step re-enqueues outbox upserts for products updated after the bulk
started, closing the dual-write window without pausing catalog writes.

Run as a Cloud Run Job in cloud environments. Local: python scripts/reindex.py
"""

import argparse
import asyncio
import json
import sys
from datetime import UTC, datetime
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "backend"))

INDEX_SPEC = REPO_ROOT / "elasticsearch" / "indexes" / "products.index.json"
BULK_PAGE = 500


def next_index_names(alias: str, current: list[str]) -> tuple[str | None, str]:
    versions = [int(name.rsplit("_v", 1)[1]) for name in current if "_v" in name]
    old_n = max(versions) if versions else 0
    old = f"{alias}_v{old_n}" if old_n else None
    return old, f"{alias}_v{old_n + 1}"


async def main(keep_old: bool) -> None:
    from app.core.config import get_settings
    from app.db.models import IndexOutbox, Product
    from app.db.session import async_session_factory
    from app.search.es_client import create_es_client
    from app.services.indexing_service import IndexingService
    from sqlalchemy import insert, literal, select

    settings = get_settings()
    alias = settings.es_products_alias
    es = create_es_client(settings)
    started_at = datetime.now(UTC)

    try:
        current = list(await es.indices.get_alias(name=alias))
        old_index, new_index = next_index_names(alias, current)
        spec = json.loads(INDEX_SPEC.read_text(encoding="utf-8"))

        print(f"Creating {new_index} (bulk-optimized settings)")
        bulk_settings = dict(spec["settings"])
        bulk_settings["index"] = {**bulk_settings.get("index", {}), "refresh_interval": "-1"}
        bulk_settings["number_of_replicas"] = 0  # restore after bulk (TECH-NOTES §3.6 #9)
        await es.indices.create(
            index=new_index, settings=bulk_settings, mappings=spec["mappings"]
        )

        indexing = IndexingService(es=es, settings=settings)
        total_indexed, total_failed, pg_total = 0, 0, 0
        async with async_session_factory() as session:
            last_id = None
            while True:
                stmt = (
                    select(Product)
                    .where(Product.is_active.is_(True))
                    .order_by(Product.id)
                    .limit(BULK_PAGE)
                )
                if last_id is not None:
                    stmt = stmt.where(Product.id > last_id)
                batch = list(await session.scalars(stmt))
                if not batch:
                    break
                indexed, failed = await indexing.bulk_index(batch, index=new_index)
                total_indexed += indexed
                total_failed += failed
                pg_total += len(batch)
                last_id = batch[-1].id
                print(f"  bulk: {total_indexed}/{pg_total} indexed")

        print("Restoring serving settings + refresh")
        await es.indices.put_settings(
            index=new_index,
            settings={"index": {"refresh_interval": "1s", "number_of_replicas": 1}},
        )
        await es.indices.refresh(index=new_index)

        es_count = (await es.count(index=new_index))["count"]
        if es_count != pg_total or total_failed:
            await es.indices.delete(index=new_index)
            sys.exit(
                f"ABORT (new index dropped): ES has {es_count} docs, PG has {pg_total}, "
                f"{total_failed} bulk failures. Alias still points at {old_index}."
            )

        print(f"Atomically swapping alias {alias}: {old_index} -> {new_index}")
        actions: list[dict] = [{"add": {"index": new_index, "alias": alias}}]
        if old_index:
            actions.insert(0, {"remove": {"index": old_index, "alias": alias}})
        await es.indices.update_aliases(actions=actions)

        # Catch-up: anything written while the bulk ran flows through the outbox
        # into the new index (the alias now points at it).
        async with async_session_factory() as session:
            result = await session.execute(
                insert(IndexOutbox).from_select(
                    ["product_id", "op"],
                    select(Product.id, literal("upsert")).where(
                        Product.updated_at >= started_at
                    ),
                )
            )
            await session.commit()
            print(f"Catch-up: enqueued {result.rowcount or 0} products updated during bulk")

        if old_index and not keep_old:
            print(f"Dropping {old_index}")
            await es.indices.delete(index=old_index)
        print(f"Done: {alias} -> {new_index} ({es_count} documents)")
    finally:
        await es.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keep-old", action="store_true", help="keep the previous index")
    args = parser.parse_args()
    asyncio.run(main(keep_old=args.keep_old))
