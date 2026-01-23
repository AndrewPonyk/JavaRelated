"""
Common/Shared Schemas
"""
from marshmallow import Schema, fields, validate


class PaginationSchema(Schema):
    """Schema for pagination parameters."""

    page = fields.Integer(load_default=1, validate=validate.Range(min=1))
    per_page = fields.Integer(
        load_default=20,
        validate=validate.Range(min=1, max=100)
    )


class PaginatedResponseSchema(Schema):
    """Schema for paginated response metadata."""

    page = fields.Integer()
    per_page = fields.Integer()
    total = fields.Integer()
    pages = fields.Integer()


class ErrorSchema(Schema):
    """Schema for error responses."""

    code = fields.String()
    message = fields.String()
    details = fields.Dict(keys=fields.String(), values=fields.String())


class ErrorResponseSchema(Schema):
    """Schema for error response wrapper."""

    error = fields.Nested(ErrorSchema)
    request_id = fields.String()
