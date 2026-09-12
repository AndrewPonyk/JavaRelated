from __future__ import annotations

import pytest

from apps.ecrf.models import FormDefinition
from apps.ecrf.services import EditCheckError, validate_datapoint

pytestmark = pytest.mark.django_db

SCHEMA = {
    "fields": {
        "systolic_bp": {"type": "number", "required": True, "min": 0, "max": 300},
        "notes": {"type": "string", "max_length": 10},
        "ae_grade": {"type": "enum", "choices": [1, 2, 3, 4, 5]},
    }
}


@pytest.fixture
def form(study):
    return FormDefinition.objects.create(study=study, name="Vitals", schema=SCHEMA)


def test_valid_number_passes(form):
    validate_datapoint(form, "systolic_bp", 120)  # no raise


def test_number_out_of_range_fails(form):
    with pytest.raises(EditCheckError):
        validate_datapoint(form, "systolic_bp", 400)


def test_required_field_missing_fails(form):
    with pytest.raises(EditCheckError):
        validate_datapoint(form, "systolic_bp", None)


def test_unknown_field_fails(form):
    with pytest.raises(EditCheckError):
        validate_datapoint(form, "not_a_field", 1)


def test_string_too_long_fails(form):
    with pytest.raises(EditCheckError):
        validate_datapoint(form, "notes", "this is way too long")


def test_enum_rejects_bad_choice(form):
    validate_datapoint(form, "ae_grade", 3)
    with pytest.raises(EditCheckError):
        validate_datapoint(form, "ae_grade", 9)


def test_api_rejects_invalid_datapoint(api, form, open_study, subject, crc):
    from apps.enrollment.services import create_enrollment

    enr = create_enrollment(study=open_study, subject=subject, created_by=crc)
    api.force_authenticate(user=crc)
    resp = api.post(
        "/api/v1/datapoints/",
        {
            "enrollment": str(enr.public_id),
            "form": str(form.public_id),
            "field_name": "systolic_bp",
            "value": 999,
        },
        format="json",
    )
    assert resp.status_code == 400
    assert resp.data["error"]["code"] == "EDIT_CHECK_FAILED"
