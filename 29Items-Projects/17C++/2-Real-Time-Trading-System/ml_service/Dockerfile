# ============================================================================
#  ml_service/Dockerfile — Python LSTM quant gRPC service.
# ============================================================================
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY proto ./proto
COPY model ./model
COPY server.py smoke_test.py ./

# Generate gRPC stubs at build time.
RUN python -m grpc_tools.protoc -I proto \
        --python_out=. --grpc_python_out=. proto/prediction.proto

EXPOSE 50051
CMD ["python", "server.py"]
