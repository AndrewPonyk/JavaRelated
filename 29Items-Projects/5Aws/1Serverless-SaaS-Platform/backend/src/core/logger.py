import json
import logging
import os
from typing import Any

# Use AWS Lambda Powertools Logger if available, with standard JSON fallback
try:
    from aws_lambda_powertools import Logger as PowertoolsLogger

    def get_logger(service_name: str | None = None) -> Any:
        svc = service_name or os.environ.get("POWERTOOLS_SERVICE_NAME", "saas-platform")
        return PowertoolsLogger(service=svc)

except ImportError:
    class StructuredLogger:
        def __init__(self, service: str):
            self.service = service
            self.logger = logging.getLogger(service)
            self.logger.setLevel(os.environ.get("LOG_LEVEL", "INFO"))

        def _format(self, level: str, msg: str, **kwargs: Any) -> str:
            payload = {
                "level": level,
                "service": self.service,
                "message": msg,
                **kwargs,
            }
            return json.dumps(payload)

        def info(self, msg: str, **kwargs: Any) -> None:
            self.logger.info(self._format("INFO", msg, **kwargs))

        def warning(self, msg: str, **kwargs: Any) -> None:
            self.logger.warning(self._format("WARNING", msg, **kwargs))

        def error(self, msg: str, **kwargs: Any) -> None:
            self.logger.error(self._format("ERROR", msg, **kwargs))

        def exception(self, msg: str, **kwargs: Any) -> None:
            self.logger.exception(self._format("ERROR", msg, **kwargs))

    def get_logger(service_name: str | None = None) -> Any:
        svc = service_name or os.environ.get("POWERTOOLS_SERVICE_NAME", "saas-platform")
        return StructuredLogger(service=svc)
