"""User service — lookup, creation, and credential verification."""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import Role, hash_password, verify_password
from app.models.user import User


class UserService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get_by_email(self, email: str) -> User | None:
        result = await self._session.execute(select(User).where(User.email == email))
        return result.scalar_one_or_none()

    async def create(
        self,
        *,
        email: str,
        password: str,
        full_name: str | None = None,
        role: Role = Role.REFERRING,
    ) -> User:
        user = User(
            email=email,
            full_name=full_name,
            hashed_password=hash_password(password),
            role=role.value,
            is_active=True,
        )
        self._session.add(user)
        await self._session.flush()
        return user

    async def authenticate(self, email: str, password: str) -> User | None:
        """Return the user iff active and the password matches, else None."""
        user = await self.get_by_email(email)
        if user is None or not user.is_active:
            return None
        if not verify_password(password, user.hashed_password):
            return None
        return user
