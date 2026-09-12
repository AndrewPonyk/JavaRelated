"""Lessons CRUD — anonymous reads, admin-gated writes."""

from __future__ import annotations

from flask import Blueprint, jsonify, request
from marshmallow import Schema, ValidationError, fields, validate

from crypto_toolkit.errors import InvalidInputError
from crypto_toolkit.services import audit_service, auth_service, lesson_service

bp = Blueprint("lessons", __name__)


class LessonCreateSchema(Schema):
    slug = fields.Str(
        required=True,
        validate=validate.Regexp(
            r"^[a-z0-9][a-z0-9-]{1,118}$", error="Slug: lowercase letters, digits, dashes."
        ),
    )
    title = fields.Str(required=True, validate=validate.Length(min=2, max=200))
    topic = fields.Str(required=True, validate=validate.OneOf(lesson_service.TOPICS))
    content_md = fields.Str(load_default="")
    demo_endpoint = fields.Str(load_default=None, allow_none=True)
    order_index = fields.Int(load_default=0)


class LessonUpdateSchema(Schema):
    title = fields.Str(validate=validate.Length(min=2, max=200))
    topic = fields.Str(validate=validate.OneOf(lesson_service.TOPICS))
    content_md = fields.Str()
    demo_endpoint = fields.Str(allow_none=True)
    order_index = fields.Int()


def _load(schema: Schema):
    body = request.get_json(silent=True)
    if body is None:
        raise InvalidInputError("Request body must be JSON.")
    try:
        return schema.load(body)
    except ValidationError as err:
        raise InvalidInputError(f"Schema validation failed: {err.messages}") from err


@bp.get("/")
def list_lessons():
    topic = request.args.get("topic")
    if topic and topic not in lesson_service.TOPICS:
        raise InvalidInputError(f"Unknown topic {topic!r}; use one of {lesson_service.TOPICS}.")
    limit = request.args.get("limit", 100, type=int)
    offset = request.args.get("offset", 0, type=int)
    return jsonify(data=lesson_service.list_lessons(topic=topic, limit=limit, offset=offset))


@bp.get("/<slug>")
def get_lesson(slug: str):
    return jsonify(data=lesson_service.get_lesson(slug))


@bp.post("/")
def create_lesson():
    auth_service.require_admin()
    lesson = lesson_service.create_lesson(_load(LessonCreateSchema()))
    audit_service.record_audit("lesson.create", {"slug": lesson["slug"]})
    return jsonify(data=lesson), 201


@bp.patch("/<slug>")
def update_lesson(slug: str):
    auth_service.require_admin()
    lesson = lesson_service.update_lesson(slug, _load(LessonUpdateSchema()))
    audit_service.record_audit("lesson.update", {"slug": slug})
    return jsonify(data=lesson)


@bp.delete("/<slug>")
def delete_lesson(slug: str):
    auth_service.require_admin()
    result = lesson_service.delete_lesson(slug)
    audit_service.record_audit("lesson.delete", {"slug": slug})
    return jsonify(data=result)
