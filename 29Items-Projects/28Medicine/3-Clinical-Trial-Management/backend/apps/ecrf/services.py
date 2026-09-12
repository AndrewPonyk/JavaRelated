"""eCRF edit checks.

Validates a captured data point against its form's field schema. The schema is a
lightweight JSON spec, e.g.::

    {"fields": {
        "systolic_bp": {"type": "number", "required": true, "min": 0, "max": 300},
        "arm_notes":   {"type": "string", "max_length": 500},
        "ae_grade":    {"type": "enum", "choices": [1, 2, 3, 4, 5]}
    }}
"""
from __future__ import annotations

from typing import Any

from apps.common.exceptions import DomainError


class EditCheckError(DomainError):
    default_code = "EDIT_CHECK_FAILED"


def validate_datapoint(form, field_name: str, value: Any) -> None:
    fields = (form.schema or {}).get("fields", {})
    spec = fields.get(field_name)
    if spec is None:
        raise EditCheckError(f"Unknown field '{field_name}' for form '{form.name}'.")

    ftype = spec.get("type", "string")
    if value is None or value == "":
        if spec.get("required"):
            raise EditCheckError(f"'{field_name}' is required.")
        return

    if ftype == "number":
        if not isinstance(value, int | float) or isinstance(value, bool):
            raise EditCheckError(f"'{field_name}' must be a number.")
        if "min" in spec and value < spec["min"]:
            raise EditCheckError(f"'{field_name}' must be >= {spec['min']}.")
        if "max" in spec and value > spec["max"]:
            raise EditCheckError(f"'{field_name}' must be <= {spec['max']}.")
    elif ftype == "string":
        if not isinstance(value, str):
            raise EditCheckError(f"'{field_name}' must be a string.")
        if "max_length" in spec and len(value) > spec["max_length"]:
            raise EditCheckError(f"'{field_name}' exceeds {spec['max_length']} chars.")
    elif ftype == "enum":
        if value not in spec.get("choices", []):
            raise EditCheckError(f"'{field_name}' must be one of {spec.get('choices')}.")
    elif ftype == "boolean":
        if not isinstance(value, bool):
            raise EditCheckError(f"'{field_name}' must be true/false.")
