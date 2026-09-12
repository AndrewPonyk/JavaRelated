"""Test setup: disable the Kafka pipeline so the API tests stay hermetic."""
import os

# Must be set before app.main is imported (pytest imports conftest first).
os.environ["ML_KAFKA_ENABLED"] = "false"
