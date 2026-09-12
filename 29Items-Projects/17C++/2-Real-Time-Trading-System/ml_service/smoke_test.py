"""
ml_service/smoke_test.py
Starts the gRPC server on an ephemeral port, calls Health and Predict, and
asserts a sane response. Run after generating stubs:

    python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. \
        proto/prediction.proto
    python smoke_test.py
"""
from __future__ import annotations

from concurrent import futures

import grpc

import prediction_pb2 as pb
import prediction_pb2_grpc as pbg
from model.lstm_model import LstmModel
from server import MODEL_VERSION, QuantPredictorServicer


def main() -> int:
    model = LstmModel.load(MODEL_VERSION)
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    pbg.add_QuantPredictorServicer_to_server(QuantPredictorServicer(model), server)
    port = server.add_insecure_port("127.0.0.1:0")
    server.start()

    try:
        channel = grpc.insecure_channel(f"127.0.0.1:{port}")
        stub = pbg.QuantPredictorStub(channel)

        health = stub.Health(pb.HealthRequest())
        assert health.ready, "service reported not ready"
        assert health.model_version == MODEL_VERSION

        req = pb.PredictRequest(model_version=MODEL_VERSION)
        fv = req.windows.add()
        fv.symbol_id = 1
        fv.timestamp_ns = 0
        fv.features.extend([0.01, 0.02, 0.015, 0.018])
        resp = stub.Predict(req)

        assert len(resp.predictions) == 1, "expected one prediction"
        p = resp.predictions[0]
        assert -1.0 <= p.signal <= 1.0, f"signal out of range: {p.signal}"
        assert 0.0 <= p.confidence <= 1.0, f"confidence out of range: {p.confidence}"

        print(
            f"SMOKE OK: ready={health.ready} model={health.model_version} "
            f"signal={p.signal:.4f} confidence={p.confidence:.4f} "
            f"inference_ns={p.inference_ns}"
        )
        channel.close()
        return 0
    finally:
        server.stop(0)


if __name__ == "__main__":
    raise SystemExit(main())
