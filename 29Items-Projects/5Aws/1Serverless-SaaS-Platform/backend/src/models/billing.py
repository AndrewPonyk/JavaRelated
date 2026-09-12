from pydantic import BaseModel


class BillingTierConfig(BaseModel):
    tier_name: str
    base_price_cents: int
    included_units: int
    overage_rate_per_unit_cents: float


class InvoicePreviewResponse(BaseModel):
    tenant_id: str
    period: str
    tier: str
    base_charge_cents: int
    total_units_consumed: int
    included_units: int
    overage_units: int
    overage_charge_cents: int
    total_due_cents: int
    currency: str = "USD"
    generated_at: str


class InvoiceListResponse(BaseModel):
    tenant_id: str
    invoices: list[InvoicePreviewResponse]
    total: int
    currency: str = "USD"


class PredictionResponse(BaseModel):
    tenant_id: str
    metric: str
    historical_30_days_sum: int
    forecast_next_7_days: int
    forecast_next_30_days: int
    model_version: str
    anomaly_risk: str
    recommended_tier_upgrade: bool
    generated_at: str
