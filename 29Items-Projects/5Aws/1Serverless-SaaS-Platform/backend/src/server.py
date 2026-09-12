"""
FastAPI Production Server & ASGI Application for Serverless SaaS Platform.
Includes GZip compression, security headers, geolocation enforcement,
and interactive OpenAPI / Swagger documentation (/docs).
"""

import os
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, Header, Query, Request, Response, status
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse

from src.core.config import get_settings
from src.core.exceptions import (
    GeolocationAccessDeniedException,
    SaaSPlatformException,
    TenantNotFoundException,
)
from src.core.logger import get_logger
from src.models.billing import InvoiceListResponse, InvoicePreviewResponse, PredictionResponse
from src.models.tenant import (
    TenantCreateRequest,
    TenantListResponse,
    TenantResponse,
    TenantUpdateRequest,
    TenantUserCreateRequest,
    TenantUserResponse,
)
from src.models.usage import (
    UsageBatchIngestRequest,
    UsageEventIngestRequest,
    UsageHistoryResponse,
    UsageMetricsResponse,
)
from src.services.billing_service import BillingService
from src.services.prediction_service import PredictionService
from src.services.tenant_service import TenantService
from src.services.usage_service import UsageService

logger = get_logger("fastapi-server")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Auto-initialize database on startup if running against DynamoDB Local
    endpoint_url = os.environ.get("DYNAMODB_ENDPOINT_URL")
    if endpoint_url:
        logger.info("Initializing and auto-seeding DynamoDB Local", endpoint_url=endpoint_url)
        try:
            from migrations.seed_tenants import seed_database
            seed_database()
        except Exception as e:
            logger.warning("Auto-seed step skipped or encountered non-fatal error", error=str(e))
    yield


app = FastAPI(
    title="Serverless SaaS Platform API",
    version="1.0.0",
    description="Multi-tenant B2B SaaS with usage-based billing, geolocation restrictions, and ML capacity forecasting.",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# 1. GZip Compression (compress payloads > 1000 bytes)
app.add_middleware(GZipMiddleware, minimum_size=1000)

# 2. CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# 3. Security Headers Middleware
@app.middleware("http")
async def add_security_headers(request: Request, call_next: Any) -> Response:
    response: Response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["X-XSS-Protection"] = "1; mode=block"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
    return response


tenant_service = TenantService()
usage_service = UsageService()
billing_service = BillingService()
prediction_service = PredictionService()


def verify_geolocation(tenant_id: str, country_header: str | None = None) -> None:
    """Enforces tenant geolocation whitelist policy."""
    settings = get_settings()
    if not settings.enforce_geolocation:
        return

    detected_country = (country_header or "US").strip().upper()
    try:
        tenant = tenant_service.get_tenant(tenant_id)
        if detected_country not in tenant.allowed_countries:
            logger.warning(
                "Access rejected by geolocation policy",
                tenant_id=tenant_id,
                country=detected_country,
                allowed=tenant.allowed_countries,
            )
            raise GeolocationAccessDeniedException(detected_country, tenant.allowed_countries)
    except TenantNotFoundException:
        # Pass to service layer to handle 404 appropriately
        pass


# ------------------------------------------------------------------------------
# EXCEPTION HANDLERS
# ------------------------------------------------------------------------------


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content=jsonable_encoder({
            "error": "VALIDATION_ERROR",
            "message": "Invalid request schema",
            "details": exc.errors(),
        }),
    )


@app.exception_handler(SaaSPlatformException)
async def saas_exception_handler(request: Request, exc: SaaSPlatformException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": exc.error_code, "message": exc.message},
    )


# ------------------------------------------------------------------------------
# SYSTEM & HEALTH ROUTES
# ------------------------------------------------------------------------------
@app.get("/health", tags=["System"])
def health_check() -> dict[str, Any]:
    return {
        "status": "HEALTHY",
        "environment": get_settings().environment,
        "service": "saas-platform",
        "version": "1.0.0",
    }


# ------------------------------------------------------------------------------
# TENANT MANAGEMENT ROUTES
# ------------------------------------------------------------------------------
@app.get("/v1/tenants", response_model=TenantListResponse, tags=["Tenants"])
def list_tenants(
    status_filter: str = Query(default="ACTIVE", alias="status"),
    limit: int = Query(default=20, ge=1, le=100),
) -> TenantListResponse:
    return tenant_service.list_tenants(status=status_filter)


@app.post("/v1/tenants", response_model=TenantResponse, status_code=status.HTTP_201_CREATED, tags=["Tenants"])
def create_tenant(request: TenantCreateRequest) -> TenantResponse:
    return tenant_service.create_tenant(request)


@app.get("/v1/tenants/{tenant_id}", response_model=TenantResponse, tags=["Tenants"])
def get_tenant(
    tenant_id: str,
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> TenantResponse:
    verify_geolocation(tenant_id, x_viewer_country)
    return tenant_service.get_tenant(tenant_id)


@app.put("/v1/tenants/{tenant_id}", response_model=TenantResponse, tags=["Tenants"])
def update_tenant(
    tenant_id: str,
    request: TenantUpdateRequest,
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> TenantResponse:
    verify_geolocation(tenant_id, x_viewer_country)
    return tenant_service.update_tenant(tenant_id, request)


@app.post(
    "/v1/tenants/{tenant_id}/users",
    response_model=TenantUserResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["Tenants"],
)
def create_tenant_user(
    tenant_id: str,
    request: TenantUserCreateRequest,
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> TenantUserResponse:
    verify_geolocation(tenant_id, x_viewer_country)
    return tenant_service.create_user(tenant_id, request)


@app.get("/v1/tenants/{tenant_id}/users", response_model=list[TenantUserResponse], tags=["Tenants"])
def list_tenant_users(
    tenant_id: str,
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> list[TenantUserResponse]:
    verify_geolocation(tenant_id, x_viewer_country)
    return tenant_service.list_users(tenant_id)


# ------------------------------------------------------------------------------
# USAGE INGESTION & METRICS ROUTES
# ------------------------------------------------------------------------------
@app.post("/v1/usage/events", status_code=status.HTTP_202_ACCEPTED, tags=["Usage"])
def ingest_usage_events(
    request: UsageBatchIngestRequest | UsageEventIngestRequest,
    x_tenant_id: str = Header(default="tenant-alpha-enterprise", alias="X-Tenant-Id"),
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> dict[str, Any]:
    verify_geolocation(x_tenant_id, x_viewer_country)

    if isinstance(request, UsageBatchIngestRequest):
        events = request.events
    else:
        events = [request]

    processed = usage_service.enqueue_usage_events(x_tenant_id, events)
    return {
        "status": "QUEUED",
        "tenant_id": x_tenant_id,
        "processed_count": processed,
        "message": "Usage events successfully buffered for aggregation.",
    }


@app.get("/v1/usage", response_model=UsageMetricsResponse, tags=["Usage"])
def get_usage_metrics(
    metric: str = Query(default="api_calls"),
    x_tenant_id: str = Header(default="tenant-alpha-enterprise", alias="X-Tenant-Id"),
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> UsageMetricsResponse:
    verify_geolocation(x_tenant_id, x_viewer_country)
    return usage_service.get_current_metrics(x_tenant_id, metric)


@app.get("/v1/usage/history", response_model=UsageHistoryResponse, tags=["Usage"])
def get_usage_history(
    metric: str = Query(default="api_calls"),
    days: int = Query(default=30, ge=1, le=90),
    x_tenant_id: str = Header(default="tenant-alpha-enterprise", alias="X-Tenant-Id"),
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> UsageHistoryResponse:
    verify_geolocation(x_tenant_id, x_viewer_country)
    return usage_service.get_usage_history(x_tenant_id, metric, days)


# ------------------------------------------------------------------------------
# BILLING ROUTES
# ------------------------------------------------------------------------------
@app.get("/v1/billing", response_model=InvoicePreviewResponse, tags=["Billing"])
def get_invoice_preview(
    metric: str = Query(default="api_calls"),
    x_tenant_id: str = Header(default="tenant-alpha-enterprise", alias="X-Tenant-Id"),
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> InvoicePreviewResponse:
    verify_geolocation(x_tenant_id, x_viewer_country)
    return billing_service.generate_invoice_preview(x_tenant_id, metric)


@app.get("/v1/billing/invoices", response_model=InvoiceListResponse, tags=["Billing"])
def list_invoices(
    x_tenant_id: str = Header(default="tenant-alpha-enterprise", alias="X-Tenant-Id"),
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> InvoiceListResponse:
    verify_geolocation(x_tenant_id, x_viewer_country)
    return billing_service.list_invoices(x_tenant_id)


# ------------------------------------------------------------------------------
# PREDICTION ROUTES
# ------------------------------------------------------------------------------
@app.get("/v1/predictions/capacity", response_model=PredictionResponse, tags=["Predictions"])
def get_capacity_prediction(
    metric: str = Query(default="api_calls"),
    x_tenant_id: str = Header(default="tenant-alpha-enterprise", alias="X-Tenant-Id"),
    x_viewer_country: str | None = Header(default=None, alias="CloudFront-Viewer-Country"),
) -> PredictionResponse:
    verify_geolocation(x_tenant_id, x_viewer_country)
    return prediction_service.get_capacity_forecast(x_tenant_id, metric)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("src.server:app", host="0.0.0.0", port=8000, reload=True)
