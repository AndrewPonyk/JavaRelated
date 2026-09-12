"""initial schema: DICOM information model + users + ml_results + audit

Revision ID: 0001_initial
Revises:
Create Date: 2026-06-27

Creates the Patient -> Study -> Series -> Instance hierarchy plus users,
ml_results, and the append-only audit_log.
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001_initial"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

_UUID = postgresql.UUID(as_uuid=True)
_TS = sa.DateTime(timezone=True)


def _ts_columns() -> list[sa.Column]:
    return [
        sa.Column("created_at", _TS, server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", _TS, server_default=sa.func.now(), nullable=False),
    ]


def upgrade() -> None:
    op.create_table(
        "patients",
        sa.Column("id", _UUID, primary_key=True),
        sa.Column("patient_id", sa.String(64), nullable=False, unique=True),
        sa.Column("patient_name", sa.String(256)),
        sa.Column("birth_date", sa.String(8)),
        sa.Column("sex", sa.String(16)),
        *_ts_columns(),
    )
    op.create_index("ix_patients_patient_id", "patients", ["patient_id"])

    op.create_table(
        "studies",
        sa.Column("id", _UUID, primary_key=True),
        sa.Column("study_instance_uid", sa.String(64), nullable=False, unique=True),
        sa.Column("accession_number", sa.String(64)),
        sa.Column("study_date", sa.String(8)),
        sa.Column("study_time", sa.String(16)),
        sa.Column("description", sa.String(256)),
        sa.Column("modalities", sa.String(64)),
        sa.Column(
            "patient_pk",
            _UUID,
            sa.ForeignKey("patients.id", ondelete="CASCADE"),
            nullable=False,
        ),
        *_ts_columns(),
    )
    op.create_index("ix_studies_study_instance_uid", "studies", ["study_instance_uid"])
    op.create_index("ix_studies_accession_number", "studies", ["accession_number"])
    op.create_index("ix_studies_patient_date", "studies", ["patient_pk", "study_date"])

    op.create_table(
        "series",
        sa.Column("id", _UUID, primary_key=True),
        sa.Column("series_instance_uid", sa.String(64), nullable=False, unique=True),
        sa.Column("modality", sa.String(16)),
        sa.Column("series_number", sa.Integer),
        sa.Column("description", sa.String(256)),
        sa.Column("body_part", sa.String(64)),
        sa.Column(
            "study_pk", _UUID, sa.ForeignKey("studies.id", ondelete="CASCADE"), nullable=False
        ),
        *_ts_columns(),
    )
    op.create_index("ix_series_series_instance_uid", "series", ["series_instance_uid"])
    op.create_index("ix_series_modality", "series", ["modality"])

    op.create_table(
        "instances",
        sa.Column("id", _UUID, primary_key=True),
        sa.Column("sop_instance_uid", sa.String(64), nullable=False, unique=True),
        sa.Column("sop_class_uid", sa.String(64)),
        sa.Column("instance_number", sa.Integer),
        sa.Column("transfer_syntax_uid", sa.String(64)),
        sa.Column("rows", sa.Integer),
        sa.Column("columns", sa.Integer),
        sa.Column("object_key", sa.String(512), nullable=False),
        sa.Column("size_bytes", sa.Integer),
        sa.Column(
            "series_pk", _UUID, sa.ForeignKey("series.id", ondelete="CASCADE"), nullable=False
        ),
        *_ts_columns(),
    )
    op.create_index("ix_instances_sop_instance_uid", "instances", ["sop_instance_uid"])

    op.create_table(
        "ml_results",
        sa.Column("id", _UUID, primary_key=True),
        sa.Column("model_name", sa.String(128), nullable=False),
        sa.Column("model_version", sa.String(64), nullable=False),
        sa.Column("predictions", postgresql.JSONB, nullable=False),
        sa.Column("top_label", sa.String(128)),
        sa.Column("top_score", sa.Float),
        sa.Column("heatmap_key", sa.String(512)),
        sa.Column(
            "instance_pk",
            _UUID,
            sa.ForeignKey("instances.id", ondelete="CASCADE"),
            nullable=False,
        ),
        *_ts_columns(),
    )

    op.create_table(
        "users",
        sa.Column("id", _UUID, primary_key=True),
        sa.Column("email", sa.String(256), nullable=False, unique=True),
        sa.Column("full_name", sa.String(256)),
        sa.Column("hashed_password", sa.String(256), nullable=False),
        sa.Column("role", sa.String(32), nullable=False, server_default="referring_physician"),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.true()),
        *_ts_columns(),
    )

    op.create_table(
        "audit_log",
        sa.Column("id", _UUID, primary_key=True),
        sa.Column("actor_id", sa.String(64)),
        sa.Column("action", sa.String(32), nullable=False),
        sa.Column("resource_type", sa.String(32), nullable=False),
        sa.Column("resource_id", sa.String(64)),
        sa.Column("outcome", sa.String(16), nullable=False, server_default="allow"),
        sa.Column("source_ip", postgresql.INET),
        sa.Column("correlation_id", sa.String(64)),
        *_ts_columns(),
    )
    op.create_index("ix_audit_log_actor_id", "audit_log", ["actor_id"])
    op.create_index("ix_audit_log_resource_id", "audit_log", ["resource_id"])


def downgrade() -> None:
    for table in (
        "audit_log",
        "users",
        "ml_results",
        "instances",
        "series",
        "studies",
        "patients",
    ):
        op.drop_table(table)
