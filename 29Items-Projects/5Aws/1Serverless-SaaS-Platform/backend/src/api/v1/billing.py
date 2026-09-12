"""
Billing API Controller.
Generates real-time invoice previews and historical invoice listings.
"""

import json
from typing import Any

from src.core.exceptions import SaaSPlatformException
from src.core.logger import get_logger
from src.services.billing_service import BillingService

logger = get_logger("billing-api")
_billing_service = BillingService()


def _format_response(status_code: int, body: dict[str, Any]) -> dict[str, Any]:
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "Content-Type,Authorization,CloudFront-Viewer-Country",
        },
        "body": json.dumps(body),
    }


def handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    http_method = event.get("httpMethod", "GET")
    path = event.get("path", "")
    authorizer_ctx = event.get("requestContext", {}).get("authorizer", {})
    tenant_id = authorizer_ctx.get("tenant_id", "tenant-alpha-enterprise")

    try:
        if http_method == "GET":
            params = event.get("queryStringParameters") or {}
            metric = params.get("metric", "api_calls")

            # Check if requesting past invoices list
            if "/invoices" in path or params.get("invoices") == "true":
                invoices_resp = _billing_service.list_invoices(tenant_id)
                return _format_response(200, invoices_resp.model_dump())

            # Otherwise return current billing preview
            invoice = _billing_service.generate_invoice_preview(tenant_id, metric)
            return _format_response(200, invoice.model_dump())
        else:
            return _format_response(405, {"error": "Method Not Allowed"})

    except SaaSPlatformException as spe:
        return _format_response(spe.status_code, {"error": spe.error_code, "message": spe.message})
    except Exception as e:
        logger.exception("Billing calculation error", error=str(e))
        return _format_response(500, {"error": "INTERNAL_SERVER_ERROR", "message": str(e)})
