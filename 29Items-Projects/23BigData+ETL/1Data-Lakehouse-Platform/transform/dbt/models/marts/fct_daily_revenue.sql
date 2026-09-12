-- Daily revenue mart consumed by BI dashboards and the Console.
-- Incremental merge keyed on (revenue_date, currency); a 3-day lookback
-- reprocesses late-arriving events without a full rebuild. `dbt build
-- --full-refresh` rebuilds from scratch after logic changes.

{{
    config(
        materialized='incremental',
        incremental_strategy='merge',
        unique_key=['revenue_date', 'currency'],
    )
}}

with orders as (

    select * from {{ ref('stg_orders') }}
    where status in ('paid', 'shipped')  -- recognized revenue only
    {% if is_incremental() %}
      and event_date >= (select max(revenue_date) from {{ this }}) - interval '3' day
    {% endif %}

)

select
    event_date                       as revenue_date,
    currency,
    count(*)                         as order_count,
    count(distinct customer_id)      as unique_customers,
    sum(amount)                      as gross_revenue,
    avg(amount)                      as avg_order_value
from orders
group by event_date, currency
