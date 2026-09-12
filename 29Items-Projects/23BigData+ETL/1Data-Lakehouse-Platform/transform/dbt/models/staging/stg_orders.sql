-- Staging: 1:1 with the Silver source — rename, cast, light business filtering.
-- No joins, no aggregation here (that belongs in marts).

with source as (

    select * from {{ source('silver', 'orders') }}

)

select
    order_id,
    customer_id,
    cast(order_ts as timestamp(6))      as ordered_at,
    lower(status)                       as status,
    cast(amount as decimal(18, 2))      as amount,
    upper(currency)                     as currency,
    event_date
from source
where status != 'cancelled'  -- revenue marts exclude cancellations by definition
