"""Seed a small, idempotent demo dataset so the running app is immediately usable.

    python manage.py seed_demo

Creates a dev admin (admin/admin), a CRC user (crc/crc-password-123), a protocol
with eligibility criteria, an open study with arms + visit schedule, and a couple
of subjects. Safe to run repeatedly. NEVER run against production data.
"""
from __future__ import annotations

from django.core.management.base import BaseCommand
from django.db import transaction

from apps.accounts.models import Role, User
from apps.patients import services as patient_services
from apps.trials import services as trial_services
from apps.trials.models import (
    Arm,
    CriterionType,
    EligibilityCriterion,
    Protocol,
    Study,
    VisitTemplate,
)


class Command(BaseCommand):
    help = "Seed an idempotent demo dataset for local development."

    @transaction.atomic
    def handle(self, *args, **options) -> None:
        if not User.objects.filter(username="admin").exists():
            User.objects.create_superuser("admin", "admin@example.gov", "admin", role=Role.SPONSOR)
            self.stdout.write("Created superuser admin/admin")

        crc, created = User.objects.get_or_create(
            username="crc", defaults={"role": Role.CRC, "email": "crc@example.gov"}
        )
        if created:
            crc.set_password("crc-password-123")
            crc.save()
            self.stdout.write("Created CRC crc/crc-password-123")

        protocol, _ = Protocol.objects.get_or_create(
            code="DEMO-001",
            version="1.0",
            defaults={"title": "Demo Diabetes Trial", "phase": "II"},
        )
        EligibilityCriterion.objects.get_or_create(
            protocol=protocol,
            type=CriterionType.INCLUSION,
            order=1,
            defaults={
                "text": "Diagnosis of type 2 diabetes",
                "coded_rule": {"keywords": ["diabetes"], "comparator": "present"},
            },
        )
        EligibilityCriterion.objects.get_or_create(
            protocol=protocol,
            type=CriterionType.EXCLUSION,
            order=1,
            defaults={
                "text": "Pregnancy",
                "coded_rule": {"keywords": ["pregnant", "pregnancy"], "comparator": "present"},
            },
        )

        study, created = Study.objects.get_or_create(
            protocol=protocol, name="Demo Study A", defaults={"target_enrollment": 50}
        )
        if created:
            Arm.objects.create(study=study, name="Treatment", allocation_ratio=1)
            Arm.objects.create(study=study, name="Placebo", allocation_ratio=1)
            VisitTemplate.objects.create(study=study, name="Baseline", day_offset=0)
            VisitTemplate.objects.create(study=study, name="Week 4", day_offset=28)
            trial_services.open_study(study)
            self.stdout.write("Created and opened Demo Study A")

        for i in range(1, 3):
            if not patient_services.Subject.objects.filter(
                subject_code__endswith=f"DEMO{i}"
            ).exists():
                patient_services.create_subject(
                    subject_code=f"S-DEMO{i}",
                    first_name=f"Demo{i}",
                    last_name="Patient",
                    date_of_birth="1975-01-01",
                    sex_at_birth="F",
                    created_by=crc,
                )

        self.stdout.write(self.style.SUCCESS("Demo data ready."))
