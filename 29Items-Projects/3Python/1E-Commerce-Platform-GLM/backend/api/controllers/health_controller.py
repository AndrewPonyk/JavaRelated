"""
Health Check Controller

Provides health check endpoints for monitoring services.
"""

from django.db import connections
from django.core.cache import cache
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
import redis


class HealthCheckView(APIView):
    """
    Health check endpoint for monitoring service status.
    """

    permission_classes = []  # Allow unrestricted access

    def get(self, request):
        """
        Check health of all services.

        Returns:
            200: All services healthy
            503: One or more services unhealthy
        """
        health_status = {
            "status": "healthy",
            "services": {}
        }

        all_healthy = True

        # Check database connection
        try:
            db_conn = connections["default"]
            db_conn.cursor()
            health_status["services"]["database"] = {
                "status": "healthy",
                "message": "Database connection successful"
            }
        except Exception as e:
            health_status["services"]["database"] = {
                "status": "unhealthy",
                "message": str(e)
            }
            all_healthy = False

        # Check cache (Redis) connection
        try:
            cache.set("health_check", "ok", timeout=10)
            value = cache.get("health_check")
            if value == "ok":
                health_status["services"]["cache"] = {
                    "status": "healthy",
                    "message": "Cache connection successful"
                }
            else:
                raise Exception("Cache readback failed")
        except Exception as e:
            health_status["services"]["cache"] = {
                "status": "unhealthy",
                "message": str(e)
            }
            all_healthy = False

        # Check Elasticsearch (optional - don't fail if unavailable)
        try:
            from elasticsearch import Elasticsearch
            from backend.core.config.settings import settings

            es = Elasticsearch([settings.elasticsearch_url])
            if es.ping():
                health_status["services"]["elasticsearch"] = {
                    "status": "healthy",
                    "message": "Elasticsearch connection successful"
                }
            else:
                health_status["services"]["elasticsearch"] = {
                    "status": "degraded",
                    "message": "Elasticsearch not responding"
                }
        except Exception as e:
            health_status["services"]["elasticsearch"] = {
                "status": "degraded",
                "message": f"Elasticsearch unavailable: {str(e)}"
            }

        # Set overall status
        if not all_healthy:
            health_status["status"] = "unhealthy"
            return Response(health_status, status=status.HTTP_503_SERVICE_UNAVAILABLE)

        return Response(health_status, status=status.HTTP_200_OK)


class LivenessProbeView(APIView):
    """
    Simple liveness probe - returns 200 if the service is running.
    """

    permission_classes = []

    def get(self, request):
        return Response(
            {"status": "alive", "service": "ecommerce-backend"},
            status=status.HTTP_200_OK
        )


class ReadinessProbeView(APIView):
    """
    Readiness probe - checks if the service is ready to accept requests.
    """

    permission_classes = []

    def get(self, request):
        """
        Check if the service is ready.

        Returns:
            200: Service is ready
            503: Service is not ready
        """
        try:
            # Basic database connection check
            db_conn = connections["default"]
            db_conn.cursor()

            return Response(
                {"status": "ready", "service": "ecommerce-backend"},
                status=status.HTTP_200_OK
            )
        except Exception as e:
            return Response(
                {"status": "not_ready", "error": str(e)},
                status=status.HTTP_503_SERVICE_UNAVAILABLE
            )
