-- Customer dimension derived from enriched orders.
-- TODO: replace derivation with a proper customer master source when available.

with enriched as (

    select * from {{ ref('int_orders_enriched') }}

)

select
    customer_id,
    min(first_order_at)    as first_order_at,
    max(ordered_at)        as last_order_at,
    max(lifetime_orders)   as lifetime_orders,
    max(lifetime_value)    as lifetime_value,
    case
        when max(lifetime_value) >= 1000 then 'vip'
        when max(lifetime_orders) > 1 then 'repeat'
        else 'new'
    end                    as customer_segment
from enriched
group by customer_id
