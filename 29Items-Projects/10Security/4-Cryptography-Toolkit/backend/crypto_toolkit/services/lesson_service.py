"""Lesson service — CRUD for educational content (admin-gated writes)."""

from __future__ import annotations

from crypto_toolkit.errors import ConflictError, NotFoundError
from crypto_toolkit.extensions import db
from crypto_toolkit.models import Lesson

TOPICS = ("aes", "rsa", "ecdsa", "sha3", "argon2", "tls")


def list_lessons(topic: str | None = None, limit: int = 100, offset: int = 0) -> dict:
    """Paginated listing; `total` is the unpaginated count for the given filter."""
    limit = max(1, min(limit, 100))
    offset = max(0, offset)
    query = Lesson.query
    if topic:
        query = query.filter_by(topic=topic)
    total = query.count()
    rows = query.order_by(Lesson.order_index, Lesson.id).offset(offset).limit(limit).all()
    return {"total": total, "items": [row.to_dict() for row in rows]}


def get_lesson(slug: str) -> dict:
    lesson = Lesson.query.filter_by(slug=slug).first()
    if lesson is None:
        raise NotFoundError(f"No lesson {slug!r}.")
    return lesson.to_dict()


def create_lesson(data: dict) -> dict:
    if Lesson.query.filter_by(slug=data["slug"]).first():
        raise ConflictError(f"Slug {data['slug']!r} already exists.")
    lesson = Lesson(
        slug=data["slug"],
        title=data["title"],
        topic=data["topic"],
        content_md=data.get("content_md", ""),
        demo_endpoint=data.get("demo_endpoint"),
        order_index=data.get("order_index", 0),
    )
    db.session.add(lesson)
    db.session.commit()
    return lesson.to_dict()


def update_lesson(slug: str, data: dict) -> dict:
    lesson = Lesson.query.filter_by(slug=slug).first()
    if lesson is None:
        raise NotFoundError(f"No lesson {slug!r}.")
    lesson.update_from(data)
    db.session.commit()
    return lesson.to_dict()


def delete_lesson(slug: str) -> dict:
    lesson = Lesson.query.filter_by(slug=slug).first()
    if lesson is None:
        raise NotFoundError(f"No lesson {slug!r}.")
    result = lesson.to_dict()
    db.session.delete(lesson)
    db.session.commit()
    return {"deleted": result["slug"], "title": result["title"]}
