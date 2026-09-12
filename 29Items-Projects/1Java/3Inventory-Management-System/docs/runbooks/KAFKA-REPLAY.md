# Kafka replay procedure

Consumers are idempotent by `(event_id, consumer_name)` in `inbox_events`. A normal offset rewind therefore does not duplicate acknowledged effects.

1. Pause the target consumer deployment and record group offsets, partition counts, lag, and replay time range.
2. Confirm the event schema version is supported by the deployed consumer.
3. Retain inbox rows and reset group offsets with `kafka-consumer-groups --reset-offsets --to-datetime ... --execute`.
4. Resume one consumer replica, monitor errors and lag, then restore its normal replica count.
5. Compare alert and audit counts before and after and record the replayed offset range.

Deleting inbox rows intentionally repeats side effects and requires change approval.
