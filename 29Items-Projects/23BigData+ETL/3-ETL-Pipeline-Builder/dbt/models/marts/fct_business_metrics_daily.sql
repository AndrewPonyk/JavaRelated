-- Daily business metrics — the governed system of record that the speed layer
-- (Redis hot store) approximates in real time and is reconciled against.
-- Incremental MERGE on (metric_date, metric_name).

{{
    config(
        materialized='incremental',
        unique_key=['metric_date', 'metric_name'],
        on_schema_change='append_new_columns',
        incremental_strategy='merge'
    )
}}

with orders as (

    select * from {{ ref('stg_orders') }}
    {% if is_incremental() %}
        -- Reprocess a 3-day tail to absorb late-arriving data.
        where ordered_at >= dateadd('day', -3, (select max(metric_date) from {{ this }}))
    {% else %}
        where ordered_at >= '{{ var("metrics_start_date") }}'
    {% endif %}

),

events as (

    select * from {{ ref('stg_stream_events') }}
    {% if is_incremental() %}
        where event_at >= dateadd('day', -3, (select max(metric_date) from {{ this }}))
    {% else %}
        where event_at >= '{{ var("metrics_start_date") }}'
    {% endif %}

),

order_metrics as (

    select
        ordered_at::date              as metric_date,
        'orders_count'                as metric_name,
        count(*)::double              as metric_value
    from orders
    group by 1

    union all

    select
        ordered_at::date,
        'revenue_total',
        sum(order_amount)::double
    from orders
    group by 1

    union all

    select
        ordered_at::date,
        'avg_order_value',
        avg(order_amount)::double
    from orders
    group by 1

),

event_metrics as (

    select
        event_at::date                as metric_date,
        'checkout_error_rate'         as metric_name,
        div0(
            count_if(event_type = 'checkout_failed'),
            count_if(event_type in ('order_placed', 'checkout_failed'))
        )::double                     as metric_value
    from events
    group by 1

)

select
    metric_date,
    metric_name,
    metric_value,
    current_timestamp() as _computed_at
from order_metrics

union all

select
    metric_date,
    metric_name,
    metric_value,
    current_timestamp() as _computed_at
from event_metrics
