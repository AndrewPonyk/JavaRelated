-- Staging: apply schema to the VARIANT landing table, dedupe on order_id
-- (at-least-once ingestion upstream ⇒ duplicates are expected, losses are not).

with source as (

    select payload, _source_file, _loaded_at
    from {{ source('raw_orders', 'orders_raw') }}

),

parsed as (

    select
        payload:order_id::string            as order_id,
        payload:customer_id::string         as customer_id,
        payload:status::string              as order_status,
        payload:amount::number(18, 2)       as order_amount,
        upper(payload:currency::string)     as currency_code,
        payload:created_at::timestamp_ntz   as ordered_at,
        _source_file,
        _loaded_at
    from source

),

deduped as (

    select
        *,
        row_number() over (
            partition by order_id
            order by _loaded_at desc
        ) as _rn
    from parsed

)

select
    order_id,
    customer_id,
    order_status,
    order_amount,
    currency_code,
    ordered_at,
    _source_file,
    _loaded_at
from deduped
where _rn = 1
