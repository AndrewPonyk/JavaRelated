"""Admin user management — list, promote/demote, enable/disable."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import Principal, require_role
from app.db.session import get_session
from app.models.user import User
from app.schemas.auth import UserRead, UserUpdate

router = APIRouter()


@router.get("", response_model=list[UserRead], summary="List users")
async def list_users(
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("admin")),
) -> list[User]:
    return list((await session.scalars(select(User).order_by(User.id))).all())


@router.get("/{user_id}", response_model=UserRead, summary="Get a user")
async def get_user(
    user_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("admin")),
) -> User:
    user = await session.get(User, user_id)
    if user is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"User {user_id} not found")
    return user


@router.patch("/{user_id}", response_model=UserRead, summary="Update role / active state")
async def update_user(
    user_id: int,
    payload: UserUpdate,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("admin")),
) -> User:
    """Admin-only mutations.

    Self-lockout guard: an admin cannot change their own role or disable
    themselves. Demoting the *last active admin* is refused so a deployment
    can never lose all admin access.
    """
    if payload.role is None and payload.is_active is None:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Nothing to update")
    user = await session.get(User, user_id)
    if user is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"User {user_id} not found")

    acting_on_self = principal.auth_type == "jwt" and principal.id == str(user.id)
    if acting_on_self and (payload.role is not None or payload.is_active is not None):
        raise HTTPException(
            status.HTTP_409_CONFLICT, "Admins cannot change their own role or active state"
        )

    if payload.role is not None:
        demoting = user.role == "admin" and payload.role != "admin"
        if demoting:
            other_admins = await session.scalar(
                select(User.id).where(
                    User.role == "admin", User.id != user.id, User.is_active.is_(True)
                )
            )
            if other_admins is None:
                raise HTTPException(status.HTTP_409_CONFLICT, "Cannot demote the last active admin")
        user.role = payload.role
    if payload.is_active is not None:
        user.is_active = payload.is_active
    await session.commit()
    await session.refresh(user)
    return user
