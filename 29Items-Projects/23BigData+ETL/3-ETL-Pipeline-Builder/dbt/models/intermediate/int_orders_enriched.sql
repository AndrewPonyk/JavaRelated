-- Intermediate (ephemeral): orders with customer attributes attached.
-- TODO: join real customer dimensions once a customer source lands;
-- currently derives a minimal profile from order history itself.

with orders as (

    select * from {{ ref('stg_orders') }}

),

customer_profile as (

    select
        customer_id,
        min(ordered_at) as first_order_at,
        count(*)        as lifetime_orders,
        sum(order_amount) as lifetime_value
    from orders
    group by customer_id

)

select
    o.order_id,
    o.customer_id,
    o.order_status,
    o.order_amount,
    o.currency_code,
    o.ordered_at,
    cp.first_order_at,
    cp.lifetime_orders,
    cp.lifetime_value,
    (o.ordered_at = cp.first_order_at) as is_first_order
from orders as o
inner join customer_profile as cp
    on o.customer_id = cp.customer_id
