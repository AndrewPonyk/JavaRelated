from uuid import UUID

from fastapi import status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.models.auth import Role, User
from app.schemas.auth import RoleCreate, RoleUpdate, UserCreate, UserRead, UserUpdate


class UserService:
    def __init__(self, db: Session) -> None:
        self.db = db

    def create_role(self, payload: RoleCreate) -> Role:
        role = Role(**payload.model_dump())
        self.db.add(role)
        self.db.commit()
        self.db.refresh(role)
        return role

    def list_roles(self, limit: int = 100, offset: int = 0) -> list[Role]:
        return list(self.db.scalars(select(Role).order_by(Role.name).limit(limit).offset(offset)))

    def update_role(self, role_id: UUID, payload: RoleUpdate) -> Role | None:
        role = self.db.get(Role, role_id)
        if role is None:
            return None
        role.description = payload.description
        self.db.commit()
        self.db.refresh(role)
        return role

    def delete_role(self, role_id: UUID) -> bool:
        role = self.db.get(Role, role_id)
        if role is None:
            return False
        if role.name in {"admin", "analyst"}:
            raise AppError("protected_role", "Built-in roles cannot be deleted", status.HTTP_400_BAD_REQUEST)
        self.db.delete(role)
        self.db.commit()
        return True

    def get_role_by_name(self, name: str) -> Role | None:
        return self.db.scalar(select(Role).where(Role.name == name))

    def create_user(self, payload: UserCreate) -> UserRead:
        roles = self._resolve_roles(payload.roles)
        user = User(
            email=str(payload.email),
            display_name=payload.display_name,
            identity_subject=payload.identity_subject,
            roles=roles,
        )
        self.db.add(user)
        self.db.commit()
        self.db.refresh(user)
        return self._to_read(user)

    def list_users(self, limit: int = 100, offset: int = 0) -> list[UserRead]:
        users = self.db.scalars(select(User).order_by(User.email).limit(limit).offset(offset)).all()
        return [self._to_read(user) for user in users]

    def get_user(self, user_id: UUID) -> UserRead | None:
        user = self.db.get(User, user_id)
        return None if user is None else self._to_read(user)

    def update_user(self, user_id: UUID, payload: UserUpdate) -> UserRead | None:
        user = self.db.get(User, user_id)
        if user is None:
            return None
        if payload.display_name is not None:
            user.display_name = payload.display_name
        if payload.roles is not None:
            user.roles = self._resolve_roles(payload.roles)
        self.db.commit()
        self.db.refresh(user)
        return self._to_read(user)

    def delete_user(self, user_id: UUID) -> bool:
        user = self.db.get(User, user_id)
        if user is None:
            return False
        self.db.delete(user)
        self.db.commit()
        return True

    def _resolve_roles(self, role_names: list[str]) -> list[Role]:
        roles: list[Role] = []
        for name in role_names:
            role = self.get_role_by_name(name)
            if role is None:
                raise AppError("role_not_found", f"Role '{name}' does not exist", status.HTTP_400_BAD_REQUEST)
            roles.append(role)
        return roles

    @staticmethod
    def _to_read(user: User) -> UserRead:
        return UserRead(
            id=user.id,
            email=user.email,
            display_name=user.display_name,
            identity_subject=user.identity_subject,
            roles=[role.name for role in user.roles],
            created_at=user.created_at,
            updated_at=user.updated_at,
        )
