#!/usr/bin/env python
"""DLQ inspection & replay — the poison-message workflow (runbook: docs/RUNBOOKS.md).

    python scripts/replay_dlq.py inspect                        # what is in logs.dlq, and why
    python scripts/replay_dlq.py dump --dir dlq-out             # payloads → files, one per msg
    python scripts/replay_dlq.py replay                         # dry-run: plan only, send nothing
    python scripts/replay_dlq.py replay --execute               # re-produce payloads to logs.raw
    python scripts/replay_dlq.py replay --from-dir dlq-out --execute  # replay hand-fixed files

Replaying an *unmodified* poison message just sends it around the loop again (the enrich
job will re-reject it into the DLQ). The intended workflows are therefore:

  * bug-was-in-the-pipeline: fix the parser, deploy, then `replay --execute` as-is;
  * bug-was-in-the-payload:  `dump`, edit the `payload` field in the files, then
    `replay --from-dir … --execute`.

Payloads that are not JSON objects are skipped (reported, never sent) — fix them via the
dump/edit path first. Replayed messages carry an `la-replayed-from` Kafka header with the
DLQ coordinates, so a second failure is traceable to its origin. The DLQ topic itself is
immutable; replayed messages age out via topic retention.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

from log_analytics.common.kafka import TOPIC_LOGS_DLQ, TOPIC_LOGS_RAW, make_producer

REPLAY_HEADER = "la-replayed-from"


@dataclass(frozen=True)
class DlqRecord:
    """One poison message, as written to logs.dlq by the enrich job's DLQ split."""

    payload: str
    error: str
    source_topic: str
    source_partition: int
    source_offset: int

    @property
    def coordinates(self) -> str:
        return f"{self.source_topic}:{self.source_partition}:{self.source_offset}"

    @property
    def filename(self) -> str:
        """Stable, sortable, collision-free name for dump files."""
        return f"{self.source_partition:04d}-{self.source_offset:012d}.json"


def parse_dlq_record(value: dict) -> DlqRecord:
    """Tolerant constructor — a malformed DLQ document must not break the tooling."""
    return DlqRecord(
        payload=str(value.get("payload") or ""),
        error=str(value.get("error") or "unknown"),
        source_topic=str(value.get("source_topic") or TOPIC_LOGS_DLQ),
        source_partition=int(value.get("source_partition") or 0),
        source_offset=int(value.get("source_offset") or 0),
    )


def summarize(records: list[DlqRecord]) -> dict[str, int]:
    """Message count per error reason, most frequent first."""
    counts: dict[str, int] = {}
    for record in records:
        counts[record.error] = counts.get(record.error, 0) + 1
    return dict(sorted(counts.items(), key=lambda kv: kv[1], reverse=True))


def parse_payload(record: DlqRecord) -> dict | None:
    """The payload as a JSON object, or None when it isn't one (not replayable as-is)."""
    try:
        parsed = json.loads(record.payload)
    except (json.JSONDecodeError, TypeError):
        return None
    return parsed if isinstance(parsed, dict) else None


@dataclass(frozen=True)
class ReplayItem:
    key: str | None
    value: dict
    origin: str  # DLQ coordinates, stamped into the la-replayed-from header


def prepare_replay(
    records: list[DlqRecord],
    *,
    error: str | None = None,
    limit: int = 0,
) -> tuple[list[ReplayItem], list[DlqRecord]]:
    """Split records into (replayable items, skipped) applying the error filter and limit.

    Skipped = payload is not a JSON object; those need the dump/edit workflow.
    """
    items: list[ReplayItem] = []
    skipped: list[DlqRecord] = []
    for record in records:
        if error and record.error != error:
            continue
        if limit and len(items) >= limit:
            break
        payload = parse_payload(record)
        if payload is None:
            skipped.append(record)
            continue
        service = payload.get("service")
        items.append(
            ReplayItem(
                key=service if isinstance(service, str) and service else None,
                value=payload,
                origin=record.coordinates,
            )
        )
    return items, skipped


# ── dump / load (the edit-and-replay workflow) ───────────────────────────────


def dump_records(records: list[DlqRecord], directory: Path) -> int:
    """One pretty-printed JSON file per record; edit `payload`, then replay --from-dir."""
    directory.mkdir(parents=True, exist_ok=True)
    for record in records:
        path = directory / record.filename
        path.write_text(json.dumps(asdict(record), indent=2), encoding="utf-8")
    return len(records)


def load_records(directory: Path) -> list[DlqRecord]:
    records = []
    for path in sorted(directory.glob("*.json")):
        records.append(parse_dlq_record(json.loads(path.read_text(encoding="utf-8"))))
    return records


# ── Kafka I/O ────────────────────────────────────────────────────────────────


async def read_dlq(bootstrap: str, *, limit: int = 0) -> list[DlqRecord]:
    """Bounded read of the whole DLQ topic (no consumer group, no offset side effects)."""
    from aiokafka import AIOKafkaConsumer, TopicPartition

    consumer = AIOKafkaConsumer(
        bootstrap_servers=bootstrap,
        enable_auto_commit=False,
        auto_offset_reset="earliest",
    )
    await consumer.start()
    try:
        await consumer.topics()  # force metadata so partitions_for_topic is populated
        partitions = consumer.partitions_for_topic(TOPIC_LOGS_DLQ) or set()
        if not partitions:
            return []
        tps = [TopicPartition(TOPIC_LOGS_DLQ, p) for p in sorted(partitions)]
        consumer.assign(tps)
        await consumer.seek_to_beginning(*tps)
        ends = await consumer.end_offsets(tps)

        records: list[DlqRecord] = []
        while True:
            if limit and len(records) >= limit:
                break
            positions = {tp: await consumer.position(tp) for tp in tps}
            if all(positions[tp] >= ends[tp] for tp in tps):
                break
            batches = await consumer.getmany(timeout_ms=2000, max_records=500)
            if not batches:
                break  # nothing arrived although positions say there should be — bail out
            for messages in batches.values():
                for message in messages:
                    try:
                        records.append(parse_dlq_record(json.loads(message.value)))
                    except (json.JSONDecodeError, TypeError):
                        # A DLQ record that is itself garbage still deserves eyeballs.
                        records.append(
                            DlqRecord(
                                payload=(message.value or b"").decode("utf-8", "replace"),
                                error="unreadable_dlq_record",
                                source_topic=message.topic,
                                source_partition=message.partition,
                                source_offset=message.offset,
                            )
                        )
        return records[:limit] if limit else records
    finally:
        await consumer.stop()


async def produce_items(items: list[ReplayItem], bootstrap: str, topic: str) -> int:
    producer = make_producer(bootstrap, client_id="dlq-replay")
    await producer.start()
    try:
        for item in items:
            await producer.send(
                topic,
                value=item.value,
                key=item.key,
                headers=[(REPLAY_HEADER, item.origin.encode("utf-8"))],
            )
        await producer.flush()
    finally:
        await producer.stop()
    return len(items)


# ── commands ─────────────────────────────────────────────────────────────────


def cmd_inspect(args: argparse.Namespace) -> int:
    records = asyncio.run(read_dlq(args.bootstrap, limit=args.limit))
    print(f"{len(records)} message(s) in {TOPIC_LOGS_DLQ}")
    for error, count in summarize(records).items():
        print(f"  {count:6d} x {error}")
    for record in records[: args.samples]:
        preview = record.payload[:160].replace("\n", "\\n")
        print(f"  [{record.coordinates}] {record.error}: {preview}")
    return 0


def cmd_dump(args: argparse.Namespace) -> int:
    records = asyncio.run(read_dlq(args.bootstrap, limit=args.limit))
    written = dump_records(records, Path(args.dir))
    print(f"wrote {written} file(s) to {args.dir} — edit `payload`, then replay --from-dir")
    return 0


def cmd_replay(args: argparse.Namespace) -> int:
    if args.from_dir:
        records = load_records(Path(args.from_dir))
        source = args.from_dir
    else:
        records = asyncio.run(read_dlq(args.bootstrap, limit=0))
        source = TOPIC_LOGS_DLQ
    items, skipped = prepare_replay(records, error=args.error, limit=args.limit)

    for record in skipped:
        print(f"skip (payload is not a JSON object) [{record.coordinates}]", file=sys.stderr)
    if not items:
        print(f"nothing to replay from {source}")
        return 0
    if not args.execute:
        for item in items[:10]:
            print(f"would send key={item.key or '-'} from [{item.origin}]")
        print(f"DRY RUN: {len(items)} message(s) would be sent to {args.topic} — use --execute")
        return 0

    sent = asyncio.run(produce_items(items, args.bootstrap, args.topic))
    print(f"replayed {sent} message(s) from {source} to {args.topic} ({len(skipped)} skipped)")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--bootstrap",
        default=os.getenv("LA_KAFKA_BOOTSTRAP_SERVERS", "localhost:29092"),
    )
    sub = parser.add_subparsers(dest="command", required=True)

    inspect = sub.add_parser("inspect", help="count DLQ messages per error reason")
    inspect.add_argument("--limit", type=int, default=0, help="read at most N messages (0 = all)")
    inspect.add_argument("--samples", type=int, default=5, help="payload previews to print")
    inspect.set_defaults(func=cmd_inspect)

    dump = sub.add_parser("dump", help="write DLQ messages to files for fixing")
    dump.add_argument("--dir", required=True, help="target directory (created if missing)")
    dump.add_argument("--limit", type=int, default=0)
    dump.set_defaults(func=cmd_dump)

    replay = sub.add_parser("replay", help="re-produce payloads to the pipeline (dry-run default)")
    replay.add_argument("--from-dir", default="", help="replay fixed files instead of the topic")
    replay.add_argument("--error", default="", help="only messages with this exact error reason")
    replay.add_argument("--limit", type=int, default=0, help="replay at most N messages (0 = all)")
    replay.add_argument("--topic", default=TOPIC_LOGS_RAW, help="target topic")
    replay.add_argument("--execute", action="store_true", help="actually send (default: dry-run)")
    replay.set_defaults(func=cmd_replay)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    sys.exit(main())
