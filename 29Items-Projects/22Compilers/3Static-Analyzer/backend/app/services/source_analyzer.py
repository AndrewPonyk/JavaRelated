from __future__ import annotations

import json
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from uuid import uuid4

from app.core.config import settings
from app.models.analysis import Finding, FindingSeverity, RuleDefinition

try:
    from z3 import Int, Solver, sat
except ImportError:  # pragma: no cover
    Int = None
    Solver = None
    sat = None


class SourceAnalysisError(Exception):
    """Raised when source cannot be read or analyzed."""


@dataclass(frozen=True)
class SourceInput:
    source_path: str
    compile_commands_path: str | None = None
    source_code: str | None = None


class SourceAnalyzer:
    unsafe_calls = {"gets", "strcpy", "strcat", "sprintf", "vsprintf", "scanf", "system", "popen"}
    taint_sources = {"getenv", "recv", "read", "scanf", "fgets", "argv"}
    taint_sinks = {"system", "popen", "execl", "execv", "execvp"}

    function_pattern = re.compile(
        r"^\s*(?:static\s+|inline\s+|extern\s+)?[\w:<>~*&\s]+\s+([A-Za-z_]\w*)\s*\([^;]*\)\s*\{?"
    )
    call_pattern = re.compile(r"\b([A-Za-z_]\w*)\s*\(")
    variable_pattern = re.compile(
        r"\b(?:int|char|long|short|bool|float|double|auto|std::string)\s+"
        r"([A-Za-z_]\w*)\b(?!\s*\()"
    )
    null_assignment_pattern = re.compile(r"\b([A-Za-z_]\w*)\s*=\s*(?:NULL|nullptr|0)\s*;")
    dereference_pattern = re.compile(r"(?:\*\s*([A-Za-z_]\w*)|([A-Za-z_]\w*)\s*->)")

    def extract_facts(self, source: SourceInput) -> dict:
        external = self._try_external_extractor(source)
        if external is not None:
            return external
        code = self._read_source(source)
        return self._extract_from_text(code, source.source_path)

    def evaluate(
        self, source: SourceInput, rules: list[RuleDefinition]
    ) -> tuple[dict, list[Finding]]:
        facts = self.extract_facts(source)
        code = facts.get("sourceText", "")
        enabled = {rule.id: rule for rule in rules}
        findings: list[Finding] = []

        for rule_id, rule in enabled.items():
            if rule_id == "security.unsafe-call":
                findings.extend(self._check_unsafe_calls(facts, rule))
            elif rule_id == "bug.null-dereference":
                findings.extend(self._check_null_dereference(code, source.source_path, rule))
            elif rule_id == "quality.unreachable-code":
                findings.extend(self._check_unreachable_code(code, source.source_path, rule))
            elif rule_id == "security.tainted-command":
                findings.extend(self._check_tainted_command(code, source.source_path, rule))
            elif rule_id == "analysis.path-feasible":
                findings.extend(self._check_path_feasible(code, source.source_path, rule))

        facts.pop("sourceText", None)
        return facts, findings

    def _read_source(self, source: SourceInput) -> str:
        if source.source_code is not None:
            if len(source.source_code.encode("utf-8")) > settings.max_source_bytes:
                raise SourceAnalysisError("source_code exceeds configured size limit")
            return source.source_code

        path = Path(source.source_path)
        if not path.exists() or not path.is_file():
            raise SourceAnalysisError(f"Source file not found: {source.source_path}")
        if path.stat().st_size > settings.max_source_bytes:
            raise SourceAnalysisError("source file exceeds configured size limit")
        return path.read_text(encoding="utf-8", errors="replace")

    def _try_external_extractor(self, source: SourceInput) -> dict | None:
        binary = Path(settings.analyzer_binary)
        if source.source_code is not None or not binary.exists():
            return None
        command = [str(binary), source.source_path]
        if source.compile_commands_path:
            command.append(source.compile_commands_path)
        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                check=True,
                text=True,
                timeout=15,
            )
            parsed = json.loads(completed.stdout)
            parsed["sourceText"] = self._read_source(source)
            return parsed
        except (subprocess.SubprocessError, json.JSONDecodeError, OSError):
            return None

    def _extract_from_text(self, code: str, source_path: str) -> dict:
        functions = []
        calls = []
        variables = []
        control_flow = []

        for index, line in enumerate(code.splitlines(), start=1):
            function_match = self.function_pattern.match(line)
            if function_match and function_match.group(1) not in {"if", "for", "while", "switch"}:
                functions.append({"name": function_match.group(1), "line": index, "column": 1})
            variables.extend(
                {"name": match.group(1), "line": index, "column": match.start(1) + 1}
                for match in self.variable_pattern.finditer(line)
            )
            for call in self.call_pattern.finditer(line):
                name = call.group(1)
                if name not in {"if", "for", "while", "switch", "return", "sizeof"}:
                    calls.append({"name": name, "line": index, "column": call.start(1) + 1})
            if re.search(r"\b(if|for|while|switch|return|break|continue)\b", line):
                control_flow.append({"text": line.strip(), "line": index, "column": 1})

        return {
            "schemaVersion": "1.0.0",
            "sourcePath": source_path,
            "functions": functions,
            "calls": calls,
            "variables": variables,
            "controlFlow": control_flow,
            "sourceText": code,
        }

    def _finding(
        self,
        rule: RuleDefinition,
        message: str,
        file_path: str,
        line: int,
        column: int,
        evidence: dict,
    ) -> Finding:
        return Finding(
            id=uuid4(),
            rule_id=rule.id,
            severity=FindingSeverity(rule.severity),
            message=message,
            file_path=file_path,
            line=line,
            column=column,
            evidence=evidence,
        )

    def _check_unsafe_calls(self, facts: dict, rule: RuleDefinition) -> list[Finding]:
        return [
            self._finding(
                rule,
                f"Unsafe C/C++ library call '{call['name']}' should be replaced or wrapped.",
                facts["sourcePath"],
                call["line"],
                call["column"],
                {"call": call["name"]},
            )
            for call in facts.get("calls", [])
            if call["name"] in self.unsafe_calls
        ]

    def _check_null_dereference(
        self, code: str, source_path: str, rule: RuleDefinition
    ) -> list[Finding]:
        null_variables: dict[str, int] = {}
        findings: list[Finding] = []
        for line_number, line in enumerate(code.splitlines(), start=1):
            for assignment in self.null_assignment_pattern.finditer(line):
                null_variables[assignment.group(1)] = line_number
            for dereference in self.dereference_pattern.finditer(line):
                variable = dereference.group(1) or dereference.group(2)
                if variable in null_variables:
                    findings.append(
                        self._finding(
                            rule,
                            f"Variable '{variable}' may be dereferenced after a null assignment.",
                            source_path,
                            line_number,
                            dereference.start() + 1,
                            {"variable": variable, "assignmentLine": null_variables[variable]},
                        )
                    )
            if re.search(r"\bif\s*\(\s*([A-Za-z_]\w*)\s*(?:!=|==)\s*(?:NULL|nullptr|0)\s*\)", line):
                checked = re.search(r"\bif\s*\(\s*([A-Za-z_]\w*)", line)
                if checked:
                    null_variables.pop(checked.group(1), None)
        return findings

    def _check_unreachable_code(
        self, code: str, source_path: str, rule: RuleDefinition
    ) -> list[Finding]:
        findings: list[Finding] = []
        terminal_seen = False
        for line_number, line in enumerate(code.splitlines(), start=1):
            stripped = line.strip()
            if not stripped or stripped in {"}", "};"}:
                terminal_seen = False if stripped.startswith("}") else terminal_seen
                continue
            if terminal_seen:
                findings.append(
                    self._finding(
                        rule,
                        "Statement is unreachable after a terminal control-flow statement.",
                        source_path,
                        line_number,
                        max(line.find(stripped) + 1, 1),
                        {"statement": stripped},
                    )
                )
                terminal_seen = False
            if re.match(r"^(return|throw|break|continue)\b", stripped):
                terminal_seen = True
        return findings

    def _check_tainted_command(
        self, code: str, source_path: str, rule: RuleDefinition
    ) -> list[Finding]:
        tainted: dict[str, int] = {}
        findings: list[Finding] = []
        assignment_pattern = re.compile(
            r"\b(?:char\s*\*|auto|std::string|const\s+char\s*\*)?"
            r"\s*([A-Za-z_]\w*)\s*=\s*(.+);"
        )
        for line_number, line in enumerate(code.splitlines(), start=1):
            assignment = assignment_pattern.search(line)
            if assignment and any(source in assignment.group(2) for source in self.taint_sources):
                tainted[assignment.group(1)] = line_number
            for sink in self.taint_sinks:
                sink_match = re.search(rf"\b{re.escape(sink)}\s*\(([^)]*)\)", line)
                if sink_match:
                    arguments = sink_match.group(1)
                    for variable, source_line in tainted.items():
                        if re.search(rf"\b{re.escape(variable)}\b", arguments):
                            findings.append(
                                self._finding(
                                    rule,
                                    (
                                        f"Tainted value '{variable}' reaches command "
                                        f"execution sink '{sink}'."
                                    ),
                                    source_path,
                                    line_number,
                                    sink_match.start() + 1,
                                    {"variable": variable, "sourceLine": source_line, "sink": sink},
                                )
                            )
        return findings

    def _check_path_feasible(
        self, code: str, source_path: str, rule: RuleDefinition
    ) -> list[Finding]:
        if Solver is None or Int is None:
            return []
        condition_pattern = re.compile(
            r"\bif\s*\(\s*([A-Za-z_]\w*)\s*([<>]=?|==|!=)\s*(-?\d+)\s*\)"
        )
        findings: list[Finding] = []
        for line_number, line in enumerate(code.splitlines(), start=1):
            match = condition_pattern.search(line)
            if not match:
                continue
            variable = Int(match.group(1))
            value = int(match.group(3))
            operator = match.group(2)
            solver = Solver()
            expression = {
                ">": variable > value,
                ">=": variable >= value,
                "<": variable < value,
                "<=": variable <= value,
                "==": variable == value,
                "!=": variable != value,
            }[operator]
            solver.add(expression)
            if solver.check() == sat:
                findings.append(
                    self._finding(
                        rule,
                        (
                            f"Condition '{match.group(0)}' is feasible under "
                            "the current local constraints."
                        ),
                        source_path,
                        line_number,
                        match.start() + 1,
                        {"condition": match.group(0), "solver": "z3"},
                    )
                )
        return findings
