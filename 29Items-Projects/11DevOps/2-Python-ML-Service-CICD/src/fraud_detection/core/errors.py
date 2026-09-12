"""Domain error types mapped to RFC 7807 problem responses by ``main.py``."""

from __future__ import annotations


class AppError(Exception):
    """Base class for domain errors carrying an HTTP status and problem text."""

    status: int = 500
    title: str = "Internal Server Error"

    def __init__(self, detail: str, *, status: int | None = None, title: str | None = None) -> None:
        super().__init__(detail)
        self.detail = detail
        if status is not None:
            self.status = status
        if title is not None:
            self.title = title


class NotFoundError(AppError):
    """The requested resource does not exist."""

    status = 404
    title = "Not Found"


class ConflictError(AppError):
    """The request conflicts with the current state of the resource."""

    status = 409
    title = "Conflict"
