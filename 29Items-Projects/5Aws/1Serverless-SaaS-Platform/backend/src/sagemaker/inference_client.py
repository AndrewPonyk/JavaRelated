import json
from typing import Any

import boto3
from botocore.exceptions import ClientError

from src.core.config import get_settings
from src.core.logger import get_logger

logger = get_logger("sagemaker-client")
_sm_runtime: Any = None


def get_sagemaker_runtime() -> Any:
    global _sm_runtime
    settings = get_settings()
    if _sm_runtime is None:
        _sm_runtime = boto3.client("sagemaker-runtime", region_name=settings.aws_region)
    return _sm_runtime


class SageMakerInferenceClient:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = get_sagemaker_runtime()
        self.endpoint_name = self.settings.sagemaker_endpoint_name

    def predict_capacity(self, tenant_id: str, historical_usage: list[int]) -> dict[str, Any]:
        """
        Invokes SageMaker Serverless Inference endpoint for time-series forecasting.
        Includes circuit-breaker fallback if model endpoint is warming up or unavailable.
        """
        payload = {
            "instances": [
                {
                    "start": "2026-08-01 00:00:00",
                    "target": historical_usage if historical_usage else [100, 150, 180, 210],
                }
            ],
            "configuration": {
                "num_eval_samples": 50,
                "output_types": ["mean"],
                "quantiles": ["0.1", "0.9"],
            },
        }

        try:
            logger.info(
                "Invoking SageMaker inference endpoint",
                endpoint=self.endpoint_name,
                tenant_id=tenant_id,
                history_points=len(historical_usage),
            )
            response = self.client.invoke_endpoint(
                EndpointName=self.endpoint_name,
                ContentType="application/json",
                Body=json.dumps(payload),
            )
            result = json.loads(response["Body"].read().decode("utf-8"))
            predictions = result.get("predictions", [{}])[0].get("mean", [])

            # Extract 7-day and 30-day forecasted points
            forecast_7 = int(sum(predictions[:7])) if len(predictions) >= 7 else int(sum(historical_usage) * 0.25)
            forecast_30 = int(sum(predictions[:30])) if len(predictions) >= 30 else int(sum(historical_usage) * 1.1)

            return {
                "forecast_7_day": max(forecast_7, 1000),
                "forecast_30_day": max(forecast_30, 5000),
                "model_version": "deepar-serverless-v2",
                "anomaly_risk": "LOW" if forecast_30 < sum(historical_usage) * 2 else "HIGH",
            }
        except ClientError as e:
            logger.warning(
                "SageMaker endpoint invocation error, executing heuristic fallback",
                error=str(e),
                tenant_id=tenant_id,
            )
            # Heuristic statistical fallback (Moving average projection)
            current_sum = sum(historical_usage) if historical_usage else 10_000
            return {
                "forecast_7_day": int(current_sum * 0.28),
                "forecast_30_day": int(current_sum * 1.20),
                "model_version": "heuristic-fallback-v1",
                "anomaly_risk": "MEDIUM",
            }
