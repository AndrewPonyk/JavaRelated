"""Shared enums and small models."""

from enum import Enum

from pydantic import BaseModel


class Severity(str, Enum):
    UNKNOWN = "unknown"
    MINOR = "minor"
    MODERATE = "moderate"
    MAJOR = "major"
    CONTRAINDICATED = "contraindicated"


class EvidenceLevel(str, Enum):
    THEORETICAL = "theoretical"
    CASE_REPORT = "case_report"
    STUDY = "study"
    ESTABLISHED = "established"


# Ordinal ranking used to sort/aggregate severities.
SEVERITY_ORDER: dict[Severity, int] = {
    Severity.UNKNOWN: 0,
    Severity.MINOR: 1,
    Severity.MODERATE: 2,
    Severity.MAJOR: 3,
    Severity.CONTRAINDICATED: 4,
}


class HealthStatus(BaseModel):
    status: str
    version: str
    neo4j_connected: bool
