from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class RoleCreate(BaseModel):
    name: str = Field(min_length=2, max_length=80, pattern="^[a-zA-Z0-9_-]+$")
    description: str | None = Field(default=None, max_length=300)


class RoleUpdate(BaseModel):
    description: str | None = Field(default=None, max_length=300)


class RoleRead(RoleCreate):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    created_at: datetime | None = None


class UserCreate(BaseModel):
    email: EmailStr
    display_name: str = Field(min_length=1, max_length=160)
    identity_subject: str = Field(min_length=1, max_length=240)
    roles: list[str] = Field(default_factory=list)


class UserUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=160)
    roles: list[str] | None = None


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    email: EmailStr
    display_name: str
    identity_subject: str
    roles: list[str]
    created_at: datetime | None = None
    updated_at: datetime | None = None
