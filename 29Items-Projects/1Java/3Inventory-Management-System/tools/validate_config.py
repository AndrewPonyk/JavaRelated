from __future__ import annotations

import json
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[1]


def validate_yaml() -> None:
    paths = [
        ROOT / "compose.yaml",
        ROOT / ".gitlab-ci.yml",
        *sorted((ROOT / "infrastructure" / "k8s").rglob("*.yaml")),
    ]
    for path in paths:
        with path.open(encoding="utf-8") as stream:
            documents = list(yaml.safe_load_all(stream))
        if not documents or any(document is None for document in documents):
            raise ValueError(f"{path.relative_to(ROOT)} contains an empty YAML document")


def validate_event_schemas() -> None:
    for path in sorted((ROOT / "shared" / "schemas").rglob("*.json")):
        with path.open(encoding="utf-8") as stream:
            schema = json.load(stream)
        Draft202012Validator.check_schema(schema)


def validate_environment_template() -> None:
    names: set[str] = set()
    for line_number, raw_line in enumerate(
        (ROOT / ".env.example").read_text(encoding="utf-8").splitlines(), start=1
    ):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f".env.example:{line_number} is not KEY=VALUE")
        name = line.split("=", 1)[0]
        if not name.replace("_", "").isalnum() or name.upper() != name:
            raise ValueError(f".env.example:{line_number} has an invalid variable name")
        if name in names:
            raise ValueError(f".env.example contains duplicate variable {name}")
        names.add(name)


if __name__ == "__main__":
    validate_yaml()
    validate_event_schemas()
    validate_environment_template()
    print("Configuration, schema, and environment templates are valid.")
