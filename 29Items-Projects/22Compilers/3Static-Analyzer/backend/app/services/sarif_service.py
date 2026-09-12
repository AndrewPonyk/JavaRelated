from app.models.analysis import AnalysisRead


class SarifService:
    def build(self, analysis: AnalysisRead) -> dict:
        rules = {
            finding.rule_id: {
                "id": finding.rule_id,
                "shortDescription": {"text": finding.rule_id},
                "defaultConfiguration": {"level": self._level(finding.severity)},
            }
            for finding in analysis.findings
        }
        return {
            "version": "2.1.0",
            "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
            "runs": [
                {
                    "tool": {
                        "driver": {
                            "name": "Static Analyzer",
                            "informationUri": "https://static-analyzer.local",
                            "rules": list(rules.values()),
                        }
                    },
                    "results": [
                        {
                            "ruleId": finding.rule_id,
                            "level": self._level(finding.severity),
                            "message": {"text": finding.message},
                            "locations": [
                                {
                                    "physicalLocation": {
                                        "artifactLocation": {"uri": finding.file_path},
                                        "region": {
                                            "startLine": finding.line,
                                            "startColumn": finding.column,
                                        },
                                    }
                                }
                            ],
                            "properties": {"evidence": finding.evidence},
                        }
                        for finding in analysis.findings
                    ],
                }
            ],
        }

    def _level(self, severity: str) -> str:
        return {
            "info": "note",
            "low": "note",
            "medium": "warning",
            "high": "error",
            "critical": "error",
        }.get(str(severity), "warning")
