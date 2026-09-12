from enum import Enum

from pydantic import BaseModel, EmailStr, Field, field_validator


class TenantTier(str, Enum):
    STARTER = "STARTER"
    PRO = "PRO"
    ENTERPRISE = "ENTERPRISE"


class TenantStatus(str, Enum):
    ACTIVE = "ACTIVE"
    SUSPENDED = "SUSPENDED"
    PENDING_VERIFICATION = "PENDING_VERIFICATION"


class TenantCreateRequest(BaseModel):
    name: str = Field(..., min_length=2, max_length=100, description="Company / Tenant legal or brand name")
    tier: TenantTier = Field(default=TenantTier.STARTER, description="Subscription plan")
    contact_email: EmailStr = Field(..., description="Primary administrative billing email")
    allowed_countries: list[str] = Field(
        default_factory=lambda: ["US", "CA", "GB", "DE"],
        description="ISO-3166 alpha-2 country codes permitted by geolocation filter",
    )
    monthly_quota: int = Field(default=100_000, ge=1_000, le=1_000_000_000, description="Monthly metered unit quota")

    @field_validator("name")
    @classmethod
    def sanitize_name(cls, v: str) -> str:
        cleaned = v.strip()
        if len(cleaned) < 2:
            raise ValueError("Tenant name must be at least 2 non-whitespace characters.")
        return cleaned

    @field_validator("allowed_countries")
    @classmethod
    def validate_country_codes(cls, v: list[str]) -> list[str]:
        if not v:
            raise ValueError("At least one country code must be specified.")
        cleaned_codes = []
        for code in v:
            c = code.strip().upper()
            if len(c) != 2 or not c.isalpha():
                raise ValueError(f"Invalid ISO-3166 alpha-2 country code: '{code}'")
            cleaned_codes.append(c)
        return list(dict.fromkeys(cleaned_codes))  # Deduplicate preserving order


class TenantUpdateRequest(BaseModel):
    name: str | None = Field(default=None, min_length=2, max_length=100)
    tier: TenantTier | None = None
    status: TenantStatus | None = None
    allowed_countries: list[str] | None = None
    monthly_quota: int | None = Field(default=None, ge=1_000, le=1_000_000_000)
    contact_email: EmailStr | None = None

    @field_validator("name")
    @classmethod
    def sanitize_name(cls, v: str | None) -> str | None:
        if v is not None:
            cleaned = v.strip()
            if len(cleaned) < 2:
                raise ValueError("Tenant name must be at least 2 non-whitespace characters.")
            return cleaned
        return v

    @field_validator("allowed_countries")
    @classmethod
    def validate_country_codes(cls, v: list[str] | None) -> list[str] | None:
        if v is not None:
            if not v:
                raise ValueError("Allowed countries list cannot be empty.")
            cleaned = []
            for code in v:
                c = code.strip().upper()
                if len(c) != 2 or not c.isalpha():
                    raise ValueError(f"Invalid country code: '{code}'")
                cleaned.append(c)
            return list(dict.fromkeys(cleaned))
        return v


class TenantResponse(BaseModel):
    tenant_id: str
    name: str
    tier: TenantTier
    status: TenantStatus
    contact_email: str
    allowed_countries: list[str]
    monthly_quota: int
    created_at: str
    updated_at: str


class TenantListResponse(BaseModel):
    tenants: list[TenantResponse]
    total: int
    next_cursor: str | None = None


class TenantUserCreateRequest(BaseModel):
    email: EmailStr
    name: str = Field(..., min_length=2, max_length=100)
    role: str = Field(default="OPERATOR", description="ADMIN, OPERATOR, or AUDITOR")

    @field_validator("name")
    @classmethod
    def sanitize_name(cls, v: str) -> str:
        return v.strip()

    @field_validator("role")
    @classmethod
    def validate_role(cls, v: str) -> str:
        allowed = {"ADMIN", "OPERATOR", "AUDITOR", "MEMBER"}
        role_upper = v.strip().upper()
        if role_upper not in allowed:
            raise ValueError(f"Role must be one of: {allowed}")
        return role_upper


class TenantUserResponse(BaseModel):
    tenant_id: str
    user_id: str
    email: str
    name: str
    role: str
    created_at: str
