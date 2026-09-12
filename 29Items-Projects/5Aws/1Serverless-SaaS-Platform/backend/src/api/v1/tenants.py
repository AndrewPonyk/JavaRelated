"""
Tenant Management API Controller.
Provides tenant onboarding, profile queries, updates, and user management.
"""

import json
from typing import Any

from pydantic import ValidationError

from src.core.exceptions import SaaSPlatformException
from src.core.logger import get_logger
from src.models.tenant import TenantCreateRequest, TenantUpdateRequest, TenantUserCreateRequest
from src.services.tenant_service import TenantService

logger = get_logger("tenants-api")
_tenant_service = TenantService()


def _format_response(status_code: int, body: dict[str, Any]) -> dict[str, Any]:
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "Content-Type,Authorization,CloudFront-Viewer-Country",
        },
        "body": json.dumps(body),
    }


def handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    http_method = event.get("httpMethod", "GET")
    authorizer_ctx = event.get("requestContext", {}).get("authorizer", {})
    default_tenant = authorizer_ctx.get("tenant_id", "tenant-alpha-enterprise")
    path_params = event.get("pathParameters") or {}
    path = event.get("path", "")

    try:
        raw_body = event.get("body")
        body_dict = json.loads(raw_body) if isinstance(raw_body, str) and raw_body else {}

        # 1. User sub-resource: /v1/tenants/{id}/users
        if "/users" in path:
            target_id = path_params.get("id", default_tenant)
            if http_method == "POST":
                user_req = TenantUserCreateRequest(**body_dict)
                user_resp = _tenant_service.create_user(target_id, user_req)
                return _format_response(201, user_resp.model_dump())
            elif http_method == "GET":
                users = _tenant_service.list_users(target_id)
                return _format_response(200, {"users": [u.model_dump() for u in users], "total": len(users)})

        # 2. Tenant collection or single resource
        if http_method == "POST":
            create_req = TenantCreateRequest(**body_dict)
            tenant_resp = _tenant_service.create_tenant(create_req)
            return _format_response(201, tenant_resp.model_dump())

        elif http_method == "PUT":
            target_id = path_params.get("id", default_tenant)
            update_req = TenantUpdateRequest(**body_dict)
            updated_resp = _tenant_service.update_tenant(target_id, update_req)
            return _format_response(200, updated_resp.model_dump())

        elif http_method == "GET":
            # If query asks for all tenants
            if path_params.get("id"):
                tenant_resp = _tenant_service.get_tenant(path_params["id"])
                return _format_response(200, tenant_resp.model_dump())
            elif event.get("queryStringParameters", {}).get("list") == "true":
                list_resp = _tenant_service.list_tenants()
                return _format_response(200, list_resp.model_dump())
            else:
                tenant_resp = _tenant_service.get_tenant(default_tenant)
                return _format_response(200, tenant_resp.model_dump())

        else:
            return _format_response(405, {"error": "Method Not Allowed"})

    except ValidationError as ve:
        return _format_response(400, {"error": "VALIDATION_ERROR", "details": ve.errors()})
    except SaaSPlatformException as spe:
        return _format_response(spe.status_code, {"error": spe.error_code, "message": spe.message})
    except Exception as e:
        logger.exception("Internal error in tenant handler", error=str(e))
        return _format_response(500, {"error": "INTERNAL_SERVER_ERROR", "message": str(e)})
