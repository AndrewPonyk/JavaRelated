"""
API Permissions

Custom permission classes for the REST API.
"""

from rest_framework import permissions


class IsVendorOrReadOnly(permissions.BasePermission):
    """
    Permission class that allows write access only to vendors.
    """

    def has_permission(self, request, view):
        """Check if user has permission."""
        if request.method in permissions.SAFE_METHODS:
            return True

        return request.user.is_authenticated and request.user.is_vendor

    def has_object_permission(self, request, view, obj):
        """Check if user has permission for specific object."""
        if request.method in permissions.SAFE_METHODS:
            return True

        # Vendors can only edit their own resources
        return obj.vendor == request.user or request.user.is_staff


class IsOwnerOrReadOnly(permissions.BasePermission):
    """
    Permission class that allows write access only to the resource owner.
    """

    def has_object_permission(self, request, view, obj):
        """Check if user owns the object."""
        if request.method in permissions.SAFE_METHODS:
            return True

        # Check if object has a user attribute
        if hasattr(obj, "user"):
            return obj.user == request.user

        # Check if object is directly owned by user
        return obj == request.user


class IsAdminUserOrReadOnly(permissions.BasePermission):
    """
    Permission class that allows write access only to admin users.
    """

    def has_permission(self, request, view):
        """Check if user is admin."""
        if request.method in permissions.SAFE_METHODS:
            return True

        return request.user.is_authenticated and request.user.is_staff
