import json
from io import BytesIO
from unittest.mock import MagicMock

from botocore.exceptions import ClientError

from src.sagemaker.inference_client import SageMakerInferenceClient


def test_sagemaker_inference_success() -> None:
    client = SageMakerInferenceClient()
    mock_sm = MagicMock()

    response_payload = {
        "predictions": [
            {
                "mean": [1000] * 35,
            }
        ]
    }
    body_mock = BytesIO(json.dumps(response_payload).encode("utf-8"))
    mock_sm.invoke_endpoint.return_value = {"Body": body_mock}
    client.client = mock_sm

    result = client.predict_capacity("tenant-sm-test", [500] * 30)

    assert result["model_version"] == "deepar-serverless-v2"
    assert result["forecast_7_day"] == 7000
    assert result["forecast_30_day"] == 30000


def test_sagemaker_inference_fallback_on_client_error() -> None:
    client = SageMakerInferenceClient()
    mock_sm = MagicMock()
    mock_sm.invoke_endpoint.side_effect = ClientError(
        {"Error": {"Code": "ModelNotReadyException", "Message": "Warming up"}},
        "InvokeEndpoint",
    )
    client.client = mock_sm

    result = client.predict_capacity("tenant-fallback-test", [1000] * 30)

    assert result["model_version"] == "heuristic-fallback-v1"
    assert result["anomaly_risk"] == "MEDIUM"
    assert result["forecast_30_day"] == int(30000 * 1.20)
