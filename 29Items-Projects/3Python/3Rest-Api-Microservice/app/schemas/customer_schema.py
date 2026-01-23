"""
Customer Serialization Schemas

|su:29) MARSHMALLOW SCHEMAS - Define how data is serialized (model->JSON) and deserialized
        (JSON->model). Also provides validation with helpful error messages.
"""
from marshmallow import Schema, fields, validate, validates, ValidationError


class CustomerSchema(Schema):
    """Schema for customer serialization/deserialization."""

    # |su:30) DUMP_ONLY FIELDS - Only included in output (serialization), not accepted in input
    id = fields.Integer(dump_only=True)
    # |su:31) VALIDATION RULES - Built-in validators for length, format (Email), allowed values (OneOf)
    name = fields.String(required=True, validate=validate.Length(min=1, max=255))
    email = fields.Email(required=True)
    phone = fields.String(validate=validate.Length(max=50), allow_none=True)
    company = fields.String(validate=validate.Length(max=255), allow_none=True)
    notes = fields.String(allow_none=True)
    status = fields.String(
        validate=validate.OneOf(['active', 'inactive', 'lead']),
        load_default='lead'  # Default value if not provided
    )
    sentiment_score = fields.Float(dump_only=True)
    created_at = fields.DateTime(dump_only=True)
    updated_at = fields.DateTime(dump_only=True)


class CustomerCreateSchema(Schema):
    """Schema for customer creation validation."""

    name = fields.String(
        required=True,
        validate=validate.Length(min=1, max=255),
        error_messages={'required': 'Name is required'}
    )
    email = fields.Email(
        required=True,
        error_messages={'required': 'Email is required', 'invalid': 'Invalid email format'}
    )
    phone = fields.String(validate=validate.Length(max=50), allow_none=True)
    company = fields.String(validate=validate.Length(max=255), allow_none=True)
    notes = fields.String(allow_none=True)
    status = fields.String(
        validate=validate.OneOf(['active', 'inactive', 'lead']),
        load_default='lead'
    )

    @validates('name')
    def validate_name(self, value, **kwargs):
        """Validate name is not just whitespace."""
        if value and not value.strip():
            raise ValidationError('Name cannot be empty or whitespace')


class CustomerUpdateSchema(Schema):
    """Schema for customer update validation."""

    name = fields.String(validate=validate.Length(min=1, max=255))
    email = fields.Email()
    phone = fields.String(validate=validate.Length(max=50), allow_none=True)
    company = fields.String(validate=validate.Length(max=255), allow_none=True)
    notes = fields.String(allow_none=True)
    status = fields.String(validate=validate.OneOf(['active', 'inactive', 'lead']))
