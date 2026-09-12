-- Business invariant: revenue can never be negative.
-- (Refunds are modeled as separate events, not negative orders.)
-- A singular test fails if any rows are returned.

select
    revenue_date,
    currency,
    gross_revenue
from {{ ref('fct_daily_revenue') }}
where gross_revenue < 0
