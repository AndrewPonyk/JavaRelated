from unittest.mock import MagicMock

from src.services.prediction_service import PredictionService


def test_prediction_service_uses_cache_when_available() -> None:
    mock_repo = MagicMock()
    mock_repo.get_capacity_prediction.return_value = {
        "historical_sum": 3_000_000,
        "forecast_7_day": 800_000,
        "forecast_30_day": 3_500_000,
        "model_version": "deepar-cached-v2",
        "anomaly_risk": "LOW",
        "generated_at": "2026-09-08T12:00:00Z",
    }
    mock_repo.get_tenant_metadata.return_value = {
        "monthly_quota": 5_000_000,
    }

    mock_sm = MagicMock()
    service = PredictionService(repo=mock_repo, sm_client=mock_sm)

    forecast = service.get_capacity_forecast("tenant-cache-test", "api_calls")

    assert forecast.tenant_id == "tenant-cache-test"
    assert forecast.forecast_next_7_days == 800_000
    assert forecast.forecast_next_30_days == 3_500_000
    assert forecast.model_version == "deepar-cached-v2"
    # SageMaker endpoint should NOT be invoked if cached
    mock_sm.predict_capacity.assert_not_called()


def test_prediction_service_invokes_sagemaker_when_not_cached() -> None:
    mock_repo = MagicMock()
    mock_repo.get_capacity_prediction.return_value = None
    mock_repo.get_tenant_metadata.return_value = {
        "monthly_quota": 1_000_000,
    }
    mock_repo.get_usage_counter.return_value = {
        "total_count": 800_000,
    }

    mock_sm = MagicMock()
    mock_sm.predict_capacity.return_value = {
        "forecast_7_day": 250_000,
        "forecast_30_day": 1_100_000,
        "model_version": "deepar-serverless-v2",
        "anomaly_risk": "HIGH",
    }

    service = PredictionService(repo=mock_repo, sm_client=mock_sm)

    forecast = service.get_capacity_forecast("tenant-uncached", "api_calls")

    assert forecast.forecast_next_30_days == 1_100_000
    assert forecast.recommended_tier_upgrade is True  # 1.1M > 1.0M quota
    mock_sm.predict_capacity.assert_called_once()
    mock_repo.put_capacity_prediction.assert_called_once()
