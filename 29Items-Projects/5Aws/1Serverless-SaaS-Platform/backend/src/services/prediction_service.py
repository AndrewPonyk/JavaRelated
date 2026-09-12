from datetime import datetime, timezone

from src.core.logger import get_logger
from src.db.single_table import SingleTableRepository
from src.models.billing import PredictionResponse
from src.sagemaker.inference_client import SageMakerInferenceClient

logger = get_logger("prediction-service")


class PredictionService:
    def __init__(
        self,
        repo: SingleTableRepository | None = None,
        sm_client: SageMakerInferenceClient | None = None,
    ):
        self.repo = repo or SingleTableRepository()
        self.sm_client = sm_client or SageMakerInferenceClient()

    def get_capacity_forecast(self, tenant_id: str, metric: str = "api_calls") -> PredictionResponse:
        """
        Retrieves cached forecast if available and fresh; otherwise invokes SageMaker
        and caches the result in DynamoDB.
        """
        now = datetime.now(timezone.utc)
        cached = self.repo.get_capacity_prediction(tenant_id, metric)

        tenant = self.repo.get_tenant_metadata(tenant_id)
        quota_limit = int(tenant.get("monthly_quota", 100_000))

        if cached:
            logger.info("Serving forecast from DynamoDB cache", tenant_id=tenant_id)
            forecast_30 = int(cached.get("forecast_30_day", 0))
            return PredictionResponse(
                tenant_id=tenant_id,
                metric=metric,
                historical_30_days_sum=int(cached.get("historical_sum", quota_limit // 2)),
                forecast_next_7_days=int(cached.get("forecast_7_day", 0)),
                forecast_next_30_days=forecast_30,
                model_version=cached.get("model_version", "cached-v1"),
                anomaly_risk=cached.get("anomaly_risk", "LOW"),
                recommended_tier_upgrade=forecast_30 > quota_limit,
                generated_at=cached.get("generated_at", now.isoformat()),
            )

        # Generate simulated 30-day historical points for model inference
        counter = self.repo.get_usage_counter(tenant_id, metric, now.strftime("%Y-%m"))
        current_total = int(counter.get("total_count", 50_000)) if counter else 50_000
        daily_avg = max(100, current_total // 30)
        history_points = [int(daily_avg * (0.8 + 0.05 * (i % 8))) for i in range(30)]

        # Call SageMaker
        sm_result = self.sm_client.predict_capacity(tenant_id, history_points)
        forecast_30 = sm_result["forecast_30_day"]

        # Cache in DynamoDB
        cache_item = {
            "historical_sum": sum(history_points),
            "forecast_7_day": sm_result["forecast_7_day"],
            "forecast_30_day": forecast_30,
            "model_version": sm_result["model_version"],
            "anomaly_risk": sm_result["anomaly_risk"],
        }
        self.repo.put_capacity_prediction(tenant_id, metric, cache_item)

        return PredictionResponse(
            tenant_id=tenant_id,
            metric=metric,
            historical_30_days_sum=sum(history_points),
            forecast_next_7_days=sm_result["forecast_7_day"],
            forecast_next_30_days=forecast_30,
            model_version=sm_result["model_version"],
            anomaly_risk=sm_result["anomaly_risk"],
            recommended_tier_upgrade=forecast_30 > quota_limit,
            generated_at=now.isoformat(),
        )
