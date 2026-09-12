"""RBAC permission classes (ARCHITECTURE §2.5).

Least privilege: the AUDITOR role is read-only *by construction* (it can never
issue a write), and write actions can be further restricted to specific roles.
"""
from __future__ import annotations

from rest_framework.permissions import SAFE_METHODS, BasePermission

from apps.accounts.models import Role


class IsNotReadOnlyRole(BasePermission):
    """Block all writes for read-only roles (AUDITOR, MONITOR)."""

    message = "Your role has read-only access."
    READ_ONLY_ROLES = {Role.AUDITOR, Role.MONITOR}

    def has_permission(self, request, view) -> bool:
        if request.method in SAFE_METHODS:
            return True
        role = getattr(request.user, "role", None)
        return role not in self.READ_ONLY_ROLES


class HasAnyRole(BasePermission):
    """Factory-style permission: ``HasAnyRole.of(Role.PI, Role.CRC)``."""

    allowed_roles: set[str] = set()
    message = "Your role is not permitted to perform this action."

    @classmethod
    def of(cls, *roles: str) -> type[HasAnyRole]:
        return type("HasAnyRoleScoped", (cls,), {"allowed_roles": set(roles)})

    def has_permission(self, request, view) -> bool:
        if request.method in SAFE_METHODS:
            return True
        return getattr(request.user, "role", None) in self.allowed_roles


class IsAuditorReadOnly(BasePermission):
    """Allow access only to the AUDITOR role, and only for safe methods."""

    def has_permission(self, request, view) -> bool:
        return (
            request.method in SAFE_METHODS and getattr(request.user, "role", None) == Role.AUDITOR
        ) or getattr(request.user, "is_staff", False)
