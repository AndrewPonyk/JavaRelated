"""
ml_service/server.py
gRPC server exposing the LSTM quant model to the C++ engine.

Design:
  - Predictions are ADVISORY. The engine never blocks on this service; it has a
    circuit breaker and a rules-only fallback.
  - Requests are batched and run on the model in one forward pass for throughput.
  - Model version is pinned by the client; mismatches are reported, not guessed.

Generate stubs first:
    python -m grpc_tools.protoc -I proto \
        --python_out=. --grpc_python_out=. proto/prediction.proto
"""
from __future__ import annotations

import logging
import time
from concurrent import futures

import grpc

# Generated from proto/prediction.proto (see header). Imported lazily so the
# file is readable before codegen has run.
try:
    import prediction_pb2 as pb
    import prediction_pb2_grpc as pb_grpc
except ImportError:  # pragma: no cover - stubs not generated yet
    pb = None
    pb_grpc = None

from model.lstm_model import LstmModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("ml_service")

MODEL_VERSION = "lstm-v3"


class QuantPredictorServicer(pb_grpc.QuantPredictorServicer if pb_grpc else object):
    """Serves predictions from a loaded LSTM model."""

    def __init__(self, model: LstmModel) -> None:
        self._model = model

    def Predict(self, request, context):  # noqa: N802 (gRPC naming)
        if request.model_version and request.model_version != MODEL_VERSION:
            context.set_code(grpc.StatusCode.FAILED_PRECONDITION)
            context.set_details(
                f"model version mismatch: client={request.model_version} "
                f"server={MODEL_VERSION}"
            )
            return pb.PredictResponse(model_version=MODEL_VERSION)

        t0 = time.perf_counter_ns()
        batch = [list(w.features) for w in request.windows]
        signals = self._model.predict_batch(batch)  # -> list[(signal, confidence)]
        inference_ns = time.perf_counter_ns() - t0

        resp = pb.PredictResponse(model_version=MODEL_VERSION)
        for window, (signal, confidence) in zip(request.windows, signals):
            resp.predictions.add(
                symbol_id=window.symbol_id,
                signal=float(signal),
                confidence=float(confidence),
                inference_ns=inference_ns,
            )
        return resp

    def Health(self, request, context):  # noqa: N802
        return pb.HealthResponse(ready=self._model.is_ready(), model_version=MODEL_VERSION)


def serve(port: int = 50051) -> None:
    model = LstmModel.load(version=MODEL_VERSION)
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=4),
        options=[("grpc.max_receive_message_length", 16 * 1024 * 1024)],
    )
    if pb_grpc is not None:
        pb_grpc.add_QuantPredictorServicer_to_server(
            QuantPredictorServicer(model), server
        )
    # Production: mTLS — server.add_secure_port with server credentials from Vault.
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    log.info("QuantPredictor serving on :%d (model=%s)", port, MODEL_VERSION)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
