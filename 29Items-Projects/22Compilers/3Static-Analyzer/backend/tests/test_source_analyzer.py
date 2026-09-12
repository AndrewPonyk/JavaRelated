from app.models.analysis import FindingSeverity, RuleDefinition
from app.services.source_analyzer import SourceAnalyzer, SourceInput


def rules() -> list[RuleDefinition]:
    return [
        RuleDefinition(
            id="security.unsafe-call",
            rule_pack="default",
            severity=FindingSeverity.high,
            description="unsafe",
            enabled=True,
        ),
        RuleDefinition(
            id="analysis.path-feasible",
            rule_pack="default",
            severity=FindingSeverity.info,
            description="feasible",
            enabled=True,
        ),
    ]


def test_source_analyzer_extracts_facts_and_findings() -> None:
    facts, findings = SourceAnalyzer().evaluate(
        SourceInput(source_path="unit.cpp", source_code="int main(){ if (x < 4) { gets(buf); } }"),
        rules(),
    )

    assert facts["schemaVersion"] == "1.0.0"
    assert any(call["name"] == "gets" for call in facts["calls"])
    assert all(variable["name"] != "main" for variable in facts["variables"])
    assert {finding.rule_id for finding in findings} == {
        "security.unsafe-call",
        "analysis.path-feasible",
    }
