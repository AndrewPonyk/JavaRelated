"""
Usage Ingestion and Metrics API Controller.
Provides high-throughput usage event ingestion, real-time metrics, and historical time-series queries.
"""

import json
from typing import Any

from pydantic import ValidationError

from src.core.exceptions import SaaSPlatformException
from src.core.logger import get_logger
from src.models.usage import UsageBatchIngestRequest, UsageEventIngestRequest
from src.services.usage_service import UsageService

logger = get_logger("usage-api")
_usage_service = UsageService()


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
    """
    API Gateway Lambda proxy handler for /v1/usage routes.
    """
    http_method = event.get("httpMethod", "GET")
    path = event.get("path", "")

    # Extract verified tenant context from custom authorizer
    authorizer_ctx = event.get("requestContext", {}).get("authorizer", {})
    tenant_id = authorizer_ctx.get("tenant_id", "tenant-alpha-enterprise")
    params = event.get("queryStringParameters") or {}

    try:
        if http_method == "POST":
            raw_body = event.get("body", "{}")
            body_dict = json.loads(raw_body) if isinstance(raw_body, str) else raw_body

            # Validate input schema using Pydantic
            if "events" in body_dict:
                batch_req = UsageBatchIngestRequest(**body_dict)
                events_to_process = batch_req.events
            else:
                single_req = UsageEventIngestRequest(**body_dict)
                events_to_process = [single_req]

            # Route to service layer
            enqueued_count = _usage_service.enqueue_usage_events(tenant_id, events_to_process)

            return _format_response(
                202,
                {
                    "status": "QUEUED",
                    "tenant_id": tenant_id,
                    "processed_count": enqueued_count,
                    "message": "Usage events successfully buffered for aggregation.",
                },
            )

        elif http_method == "GET":
            metric = params.get("metric", "api_calls")

            # Check if requesting historical time-series
            if "/history" in path or params.get("history") == "true":
                days = int(params.get("days", 30))
                history_resp = _usage_service.get_usage_history(tenant_id, metric, days)
                return _format_response(200, history_resp.model_dump())

            # Otherwise return current metrics
            metrics = _usage_service.get_current_metrics(tenant_id, metric)
            return _format_response(200, metrics.model_dump())

        else:
            return _format_response(405, {"error": "Method Not Allowed"})

    except ValidationError as ve:
        logger.warning("Input validation failed", error=str(ve))
        return _format_response(400, {"error": "VALIDATION_ERROR", "details": ve.errors()})
    except SaaSPlatformException as spe:
        logger.error("Domain exception encountered", error=spe.message)
        return _format_response(spe.status_code, {"error": spe.error_code, "message": spe.message})
    except Exception as e:
        logger.exception("Unhandled server error", error=str(e))
        return _format_response(500, {"error": "INTERNAL_SERVER_ERROR", "message": str(e)})
