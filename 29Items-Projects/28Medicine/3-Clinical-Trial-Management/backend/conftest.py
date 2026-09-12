"""Shared pytest fixtures (factory-style helpers).

Keeps tests declarative: a role-scoped user, an authenticated API client, and
ready-made trial/study/subject objects.
"""
from __future__ import annotations

import datetime

import pytest
from rest_framework.test import APIClient

from apps.accounts.models import Role, User
from apps.patients import services as patient_services
from apps.trials import services as trial_services
from apps.trials.models import Arm, Protocol, Study


@pytest.fixture
def make_user(db):
    def _make(username="user", role=Role.CRC, password="Sup3r-Secret-Pw!", **extra):
        return User.objects.create_user(username=username, password=password, role=role, **extra)

    return _make


@pytest.fixture
def crc(make_user):
    return make_user(username="crc", role=Role.CRC)


@pytest.fixture
def pi(make_user):
    return make_user(username="pi", role=Role.PI)


@pytest.fixture
def auditor(make_user):
    return make_user(username="auditor", role=Role.AUDITOR)


@pytest.fixture
def monitor(make_user):
    return make_user(username="monitor", role=Role.MONITOR)


@pytest.fixture
def api():
    return APIClient()


@pytest.fixture
def auth_api(api, crc):
    api.force_authenticate(user=crc)
    api.handler._force_user = crc  # noqa: SLF001
    return api


@pytest.fixture
def protocol(db):
    proto = Protocol.objects.create(code="ACME-001", title="A trial", version="1.0", phase="II")
    return proto


@pytest.fixture
def study(protocol):
    return Study.objects.create(protocol=protocol, name="Study A", target_enrollment=100)


@pytest.fixture
def open_study(study):
    Arm.objects.create(study=study, name="Treatment", allocation_ratio=1)
    Arm.objects.create(study=study, name="Placebo", allocation_ratio=1)
    return trial_services.open_study(study)


@pytest.fixture
def subject(db):
    return patient_services.create_subject(
        first_name="Jane", last_name="Doe", date_of_birth="1980-05-01", sex_at_birth="F"
    )


@pytest.fixture
def today():
    return datetime.date(2025, 6, 1)
