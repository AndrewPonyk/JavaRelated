from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware

from app.api.v1.routes.analysis import router as analysis_router
from app.api.v1.routes.classification_jobs import router as classification_jobs_router
from app.api.v1.routes.datasets import router as datasets_router
from app.api.v1.routes.layers import router as layers_router
from app.api.v1.routes.users import router as users_router
from app.core.config import get_settings
from app.core.errors import register_error_handlers
from app.core.logging import configure_logging
from app.core.middleware import RequestContextMiddleware, SecurityHeadersMiddleware

settings = get_settings()
configure_logging(settings.log_level)

app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    description="Enterprise geospatial analytics API with PostGIS and ML workflows.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(GZipMiddleware, minimum_size=1_000)
app.add_middleware(SecurityHeadersMiddleware)
app.add_middleware(RequestContextMiddleware)

app.include_router(datasets_router, prefix="/api/v1/datasets", tags=["datasets"])
app.include_router(layers_router, prefix="/api/v1/layers", tags=["layers"])
app.include_router(analysis_router, prefix="/api/v1/analysis", tags=["analysis"])
app.include_router(classification_jobs_router, prefix="/api/v1/classification-jobs", tags=["classification-jobs"])
app.include_router(users_router, prefix="/api/v1/users", tags=["users"])
register_error_handlers(app)


@app.get("/health", tags=["system"])
def health_check() -> dict[str, str]:
    return {"status": "ok", "service": settings.app_name}
