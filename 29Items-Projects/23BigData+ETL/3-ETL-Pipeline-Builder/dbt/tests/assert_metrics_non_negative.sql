-- Singular test: no business metric may ever go negative.
-- Returning rows = failing rows.

select
    metric_date,
    metric_name,
    metric_value
from {{ ref('fct_business_metrics_daily') }}
where metric_value < 0
