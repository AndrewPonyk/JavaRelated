"""UserService + security primitive tests."""

from __future__ import annotations

from app.core.security import (
    Role,
    Scope,
    create_access_token,
    decode_token,
    has_scope,
    hash_password,
    verify_password,
)
from app.services.user_service import UserService


async def test_create_and_authenticate(db):
    svc = UserService(db)
    await svc.create(email="u@x.io", password="password1", role=Role.RADIOLOGIST)
    await db.commit()

    assert await svc.authenticate("u@x.io", "password1") is not None
    assert await svc.authenticate("u@x.io", "wrong") is None
    assert await svc.authenticate("missing@x.io", "password1") is None


async def test_inactive_user_cannot_authenticate(db):
    svc = UserService(db)
    user = await svc.create(email="i@x.io", password="password1")
    user.is_active = False
    await db.commit()
    assert await svc.authenticate("i@x.io", "password1") is None


def test_password_hash_roundtrip():
    h = hash_password("secret-pass")
    assert h != "secret-pass"
    assert verify_password("secret-pass", h)
    assert not verify_password("nope", h)


def test_jwt_encodes_role_scopes():
    token = create_access_token("user@x.io", Role.RADIOLOGIST)
    claims = decode_token(token)
    assert claims["sub"] == "user@x.io"
    assert Scope.STUDY_READ.value in claims["scopes"]


def test_admin_scope_is_wildcard():
    assert has_scope([Scope.ADMIN.value], Scope.STUDY_WRITE) is True
    assert has_scope([Scope.STUDY_READ.value], Scope.STUDY_WRITE) is False
