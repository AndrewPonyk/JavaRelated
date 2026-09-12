from pathlib import Path

import yaml
from pydantic import BaseModel, Field, ValidationError

from app.core.config import settings
from app.models.analysis import FindingSeverity, RuleDefinition


class RuleConfigError(Exception):
    """Raised when the rule configuration cannot be loaded or validated."""


class RuleConfigItem(BaseModel):
    id: str = Field(min_length=1)
    enabled: bool = True
    severity: FindingSeverity
    description: str = Field(min_length=1)


class RulePackConfig(BaseModel):
    description: str = ""
    rules: list[RuleConfigItem]


class RuleConfigFile(BaseModel):
    rulePacks: dict[str, RulePackConfig]


class RuleService:
    def __init__(self, rules_path: str | None = None) -> None:
        self.rules_path = Path(rules_path or settings.rules_config)
        self._config: RuleConfigFile | None = None

    def list_rules(self, rule_pack: str | None = None) -> list[RuleDefinition]:
        config = self._load()
        if rule_pack is not None and rule_pack not in config.rulePacks:
            available = ", ".join(sorted(config.rulePacks))
            raise RuleConfigError(f"Unknown rule pack '{rule_pack}'. Available packs: {available}")
        packs = (
            {rule_pack: config.rulePacks[rule_pack]}
            if rule_pack is not None
            else config.rulePacks
        )
        return [
            RuleDefinition(
                id=rule.id,
                rule_pack=pack_name,
                severity=rule.severity,
                description=rule.description,
                enabled=rule.enabled,
            )
            for pack_name, pack in packs.items()
            for rule in pack.rules
        ]

    def enabled_rules(self, rule_pack: str) -> list[RuleDefinition]:
        rules = [rule for rule in self.list_rules(rule_pack) if rule.enabled]
        if not rules:
            available = ", ".join(sorted(self._load().rulePacks))
            message = f"Rule pack '{rule_pack}' has no enabled rules. Available packs: {available}"
            raise RuleConfigError(message)
        return rules

    def _load(self) -> RuleConfigFile:
        if self._config is not None:
            return self._config
        if not self.rules_path.exists():
            raise RuleConfigError(f"Rule configuration not found: {self.rules_path}")
        try:
            data = yaml.safe_load(self.rules_path.read_text(encoding="utf-8")) or {}
            self._config = RuleConfigFile.model_validate(data)
        except (OSError, ValidationError, yaml.YAMLError) as exc:
            raise RuleConfigError(f"Invalid rule configuration: {exc}") from exc
        return self._config
