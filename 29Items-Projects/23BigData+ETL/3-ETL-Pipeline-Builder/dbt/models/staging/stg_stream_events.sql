-- Staging: unpack the Kafka event envelope written by the Snowflake connector.
-- Dedupe on event_id — the streaming plane is at-least-once by design.

with source as (

    select record_content, _ingested_at
    from {{ source('raw_events', 'stream_events') }}

),

parsed as (

    select
        record_content:event_id::string       as event_id,
        record_content:event_type::string     as event_type,
        to_timestamp_ntz(record_content:ts_ms::number, 3) as event_at,
        record_content:amount::number(18, 2)  as amount,
        record_content:customer_id::string    as customer_id,
        record_content                        as raw_payload,
        _ingested_at
    from source

),

deduped as (

    select
        *,
        row_number() over (
            partition by event_id
            order by _ingested_at desc
        ) as _rn
    from parsed

)

select
    event_id,
    event_type,
    event_at,
    amount,
    customer_id,
    raw_payload,
    _ingested_at
from deduped
where _rn = 1
