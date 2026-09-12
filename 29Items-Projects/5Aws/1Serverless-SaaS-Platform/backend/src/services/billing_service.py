from datetime import datetime, timezone

from src.core.logger import get_logger
from src.db.single_table import SingleTableRepository
from src.models.billing import BillingTierConfig, InvoiceListResponse, InvoicePreviewResponse

logger = get_logger("billing-service")

TIER_PRICING: dict[str, BillingTierConfig] = {
    "STARTER": BillingTierConfig(
        tier_name="STARTER",
        base_price_cents=4900,  # $49.00 / mo
        included_units=100_000,
        overage_rate_per_unit_cents=0.08,  # $0.0008 per unit
    ),
    "PRO": BillingTierConfig(
        tier_name="PRO",
        base_price_cents=19900,  # $199.00 / mo
        included_units=1_000_000,
        overage_rate_per_unit_cents=0.04,  # $0.0004 per unit
    ),
    "ENTERPRISE": BillingTierConfig(
        tier_name="ENTERPRISE",
        base_price_cents=89900,  # $899.00 / mo
        included_units=10_000_000,
        overage_rate_per_unit_cents=0.015,  # $0.00015 per unit
    ),
}


class BillingService:
    def __init__(self, repo: SingleTableRepository | None = None):
        self.repo = repo or SingleTableRepository()

    def generate_invoice_preview(self, tenant_id: str, metric: str = "api_calls") -> InvoicePreviewResponse:
        """Calculates current billing period charges including base fee and overage fees."""
        now = datetime.now(timezone.utc)
        current_period = now.strftime("%Y-%m")

        tenant = self.repo.get_tenant_metadata(tenant_id)
        tier_name = tenant.get("tier", "STARTER").upper()
        pricing = TIER_PRICING.get(tier_name, TIER_PRICING["STARTER"])

        counter = self.repo.get_usage_counter(tenant_id, metric, current_period)
        consumed = int(counter.get("total_count", 0)) if counter else 0

        included = pricing.included_units
        overage_units = max(0, consumed - included)
        overage_charge = int(round(overage_units * pricing.overage_rate_per_unit_cents))
        total_due = pricing.base_price_cents + overage_charge

        logger.info(
            "Calculated invoice preview",
            tenant_id=tenant_id,
            tier=tier_name,
            consumed=consumed,
            total_due_cents=total_due,
        )

        return InvoicePreviewResponse(
            tenant_id=tenant_id,
            period=current_period,
            tier=tier_name,
            base_charge_cents=pricing.base_price_cents,
            total_units_consumed=consumed,
            included_units=included,
            overage_units=overage_units,
            overage_charge_cents=overage_charge,
            total_due_cents=total_due,
            currency="USD",
            generated_at=now.isoformat(),
        )

    def list_invoices(self, tenant_id: str) -> InvoiceListResponse:
        """Fetch past invoices and current period preview for tenant."""
        raw_invoices = self.repo.list_invoices(tenant_id)
        current_preview = self.generate_invoice_preview(tenant_id)

        invoices = [current_preview]
        for inv in raw_invoices:
            invoices.append(
                InvoicePreviewResponse(
                    tenant_id=tenant_id,
                    period=inv.get("period", "2026-08"),
                    tier=inv.get("tier", "ENTERPRISE"),
                    base_charge_cents=int(inv.get("base_charge_cents", 89900)),
                    total_units_consumed=int(inv.get("total_units_consumed", 3200000)),
                    included_units=int(inv.get("included_units", 10000000)),
                    overage_units=int(inv.get("overage_units", 0)),
                    overage_charge_cents=int(inv.get("overage_charge_cents", 0)),
                    total_due_cents=int(inv.get("total_due_cents", 89900)),
                    currency="USD",
                    generated_at=inv.get("created_at", datetime.now(timezone.utc).isoformat()),
                )
            )

        return InvoiceListResponse(tenant_id=tenant_id, invoices=invoices, total=len(invoices))
