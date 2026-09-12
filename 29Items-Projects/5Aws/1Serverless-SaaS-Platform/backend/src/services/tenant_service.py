import uuid
from datetime import datetime, timezone

from src.core.logger import get_logger
from src.db.single_table import SingleTableRepository
from src.events.publisher import publish_domain_event
from src.models.tenant import (
    TenantCreateRequest,
    TenantListResponse,
    TenantResponse,
    TenantStatus,
    TenantTier,
    TenantUpdateRequest,
    TenantUserCreateRequest,
    TenantUserResponse,
)

logger = get_logger("tenant-service")


class TenantService:
    def __init__(self, repo: SingleTableRepository | None = None):
        self.repo = repo or SingleTableRepository()

    def create_tenant(self, request: TenantCreateRequest) -> TenantResponse:
        """Onboard a new B2B tenant."""
        tenant_id = f"tenant-{uuid.uuid4().hex[:8]}"
        now = datetime.now(timezone.utc).isoformat()

        tenant_item = {
            "tenant_id": tenant_id,
            "name": request.name,
            "tier": request.tier.value,
            "status": TenantStatus.ACTIVE.value,
            "contact_email": request.contact_email,
            "allowed_countries": request.allowed_countries,
            "monthly_quota": request.monthly_quota,
            "created_at": now,
            "updated_at": now,
        }

        self.repo.put_tenant_metadata(tenant_item)
        logger.info("New tenant onboarded", tenant_id=tenant_id, tier=request.tier.value)

        # Emit TenantCreated event to EventBridge
        try:
            publish_domain_event(
                detail_type="TenantCreated",
                detail={"tenant_id": tenant_id, "name": request.name, "tier": request.tier.value},
                source="saas.tenant",
            )
        except Exception as e:
            logger.warning("Domain event publication skipped or failed", error=str(e))

        return TenantResponse(**tenant_item)

    def get_tenant(self, tenant_id: str) -> TenantResponse:
        """Fetch tenant metadata by ID."""
        data = self.repo.get_tenant_metadata(tenant_id)
        return TenantResponse(
            tenant_id=data["tenant_id"],
            name=data["name"],
            tier=TenantTier(data.get("tier", "STARTER")),
            status=TenantStatus(data.get("status", "ACTIVE")),
            contact_email=data["contact_email"],
            allowed_countries=data.get("allowed_countries", ["US"]),
            monthly_quota=int(data.get("monthly_quota", 100_000)),
            created_at=data["created_at"],
            updated_at=data["updated_at"],
        )

    def update_tenant(self, tenant_id: str, request: TenantUpdateRequest) -> TenantResponse:
        """Update existing tenant profile, quota, or allowed countries."""
        updates: dict[str, object] = {}
        if request.name is not None:
            updates["name"] = request.name
        if request.tier is not None:
            updates["tier"] = request.tier.value
        if request.status is not None:
            updates["status"] = request.status.value
        if request.allowed_countries is not None:
            updates["allowed_countries"] = request.allowed_countries
        if request.monthly_quota is not None:
            updates["monthly_quota"] = request.monthly_quota
        if request.contact_email is not None:
            updates["contact_email"] = request.contact_email

        updated_item = self.repo.update_tenant_metadata(tenant_id, updates)
        logger.info("Updated tenant profile", tenant_id=tenant_id, fields=list(updates.keys()))

        return TenantResponse(
            tenant_id=tenant_id,
            name=updated_item["name"],
            tier=TenantTier(updated_item.get("tier", "STARTER")),
            status=TenantStatus(updated_item.get("status", "ACTIVE")),
            contact_email=updated_item["contact_email"],
            allowed_countries=updated_item.get("allowed_countries", ["US"]),
            monthly_quota=int(updated_item.get("monthly_quota", 100_000)),
            created_at=updated_item["created_at"],
            updated_at=updated_item["updated_at"],
        )

    def list_tenants(self, status: str = "ACTIVE") -> TenantListResponse:
        """List tenants by active status."""
        items = self.repo.list_tenants(status=status)
        tenants = [
            TenantResponse(
                tenant_id=item["tenant_id"],
                name=item["name"],
                tier=TenantTier(item.get("tier", "STARTER")),
                status=TenantStatus(item.get("status", "ACTIVE")),
                contact_email=item["contact_email"],
                allowed_countries=item.get("allowed_countries", ["US"]),
                monthly_quota=int(item.get("monthly_quota", 100_000)),
                created_at=item["created_at"],
                updated_at=item["updated_at"],
            )
            for item in items
        ]
        return TenantListResponse(tenants=tenants, total=len(tenants))

    def create_user(self, tenant_id: str, request: TenantUserCreateRequest) -> TenantUserResponse:
        """Add a team member to a tenant."""
        user_id = f"usr-{uuid.uuid4().hex[:8]}"
        now = datetime.now(timezone.utc).isoformat()
        user_data = {
            "tenant_id": tenant_id,
            "user_id": user_id,
            "email": request.email,
            "name": request.name,
            "role": request.role,
            "created_at": now,
        }
        created = self.repo.create_tenant_user(tenant_id, user_data)
        return TenantUserResponse(**created)

    def list_users(self, tenant_id: str) -> list[TenantUserResponse]:
        """List team members under a tenant."""
        users = self.repo.list_tenant_users(tenant_id)
        return [
            TenantUserResponse(
                tenant_id=tenant_id,
                user_id=u["user_id"],
                email=u["email"],
                name=u.get("name", ""),
                role=u.get("role", "MEMBER"),
                created_at=u.get("created_at", ""),
            )
            for u in users
        ]
