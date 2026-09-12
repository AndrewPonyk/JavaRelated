import os
import sys

# Ensure backend root is on sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

# Set environment variables for tests
os.environ["ENVIRONMENT"] = "test"
os.environ["AWS_REGION"] = "us-east-1"
os.environ["DYNAMODB_TABLE_NAME"] = "saas_platform_core_test"
os.environ["EVENT_BUS_NAME"] = "saas-platform-eventbus-test"
os.environ["ENFORCE_GEOLOCATION"] = "true"
os.environ["POWERTOOLS_SERVICE_NAME"] = "test-service"
