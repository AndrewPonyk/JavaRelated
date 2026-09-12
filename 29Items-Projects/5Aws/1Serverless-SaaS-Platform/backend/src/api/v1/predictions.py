"""
Machine Learning Capacity Prediction API Controller.
Provides ML-forecasted usage predictions powered by Amazon SageMaker.
"""

import json
from typing import Any

from src.core.exceptions import SaaSPlatformException
from src.core.logger import get_logger
from src.services.prediction_service import PredictionService

logger = get_logger("predictions-api")
_prediction_service = PredictionService()


def _format_response(status_code: int, body: dict[str, Any]) -> dict[str, Any]:
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "Content-Type,Authorization",
        },
        "body": json.dumps(body),
    }


def handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    http_method = event.get("httpMethod", "GET")
    authorizer_ctx = event.get("requestContext", {}).get("authorizer", {})
    tenant_id = authorizer_ctx.get("tenant_id", "tenant-alpha-enterprise")

    try:
        if http_method == "GET":
            params = event.get("queryStringParameters") or {}
            metric = params.get("metric", "api_calls")
            forecast = _prediction_service.get_capacity_forecast(tenant_id, metric)
            return _format_response(200, forecast.model_dump())
        else:
            return _format_response(405, {"error": "Method Not Allowed"})

    except SaaSPlatformException as spe:
        return _format_response(spe.status_code, {"error": spe.error_code, "message": spe.message})
    except Exception as e:
        logger.exception("Prediction forecasting error", error=str(e))
        return _format_response(500, {"error": "INTERNAL_SERVER_ERROR", "message": str(e)})
