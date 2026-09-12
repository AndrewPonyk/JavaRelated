"""Report export endpoints — HTML summary and SARIF for CI integration."""

from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import HTMLResponse, JSONResponse
from jinja2 import Environment, FileSystemLoader, select_autoescape
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import Principal, require_role
from app.db.base import utcnow
from app.db.session import get_session
from app.models.finding import Finding, Severity
from app.models.scan import Scan
from app.services.ml_classifier import classifier

router = APIRouter()

# Autoescape is mandatory: finding titles/URLs/evidence contain attacker-
# controlled content — the report itself must not become an XSS vector.
_TEMPLATE_DIR = Path(__file__).resolve().parents[2] / "templates"
_env = Environment(
    loader=FileSystemLoader(_TEMPLATE_DIR),
    autoescape=select_autoescape(["html", "xml"]),
)
_SEVERITY_ORDER = [
    Severity.CRITICAL,
    Severity.HIGH,
    Severity.MEDIUM,
    Severity.LOW,
    Severity.INFO,
]


async def _load_scan(scan_id: int, session: AsyncSession) -> tuple[Scan, list[Finding]]:
    scan = await session.get(Scan, scan_id)
    if scan is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"Scan {scan_id} not found")
    findings = (await session.scalars(select(Finding).where(Finding.scan_id == scan_id))).all()
    ordered = sorted(findings, key=lambda f: _SEVERITY_ORDER.index(f.severity))
    return scan, ordered


@router.get("/{scan_id}/sarif", response_class=JSONResponse, summary="SARIF 2.1.0 export")
async def export_sarif(
    scan_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> dict:
    """OASIS SARIF — consumable by GitHub Code Scanning."""
    scan, findings = await _load_scan(scan_id, session)
    return {
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "version": "2.1.0",
        "runs": [
            {
                "tool": {"driver": {"name": "web-app-security-scanner", "version": "0.1.0"}},
                "results": [
                    {
                        "ruleId": f.rule_id,
                        "level": f.severity.value,
                        "message": {"text": f.title},
                        "locations": [{"physicalLocation": {"address": {"fullyQualified": f.url}}}],
                        "properties": {"owasp": f.owasp_category, "cwe": f.cwe_id},
                    }
                    for f in findings
                ],
            }
        ],
    }


@router.get("/{scan_id}/html", response_class=HTMLResponse, summary="HTML report")
async def export_html(
    scan_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> str:
    """Standalone HTML report (Jinja2, autoescaped) — printable, self-contained."""
    scan, findings = await _load_scan(scan_id, session)
    counts = [(sev, sum(1 for f in findings if f.severity == sev)) for sev in _SEVERITY_ORDER]
    return _env.get_template("report.html").render(
        scan=scan,
        findings=findings,
        counts=[(sev, n) for sev, n in counts if n],
        total=len(findings),
        error_detail=scan.error_detail,
        model_version=classifier.model_version,
        generated_at=utcnow().strftime("%Y-%m-%d %H:%M UTC"),
    )
