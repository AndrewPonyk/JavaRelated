from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class DPResult:
    algorithm: str
    variant: str
    value: Any
    details: dict[str, Any] = field(default_factory=dict)

    def describe(self) -> str:
        suffix = ""
        if self.details:
            parts = [f"{key}={value}" for key, value in self.details.items()]
            suffix = " | " + ", ".join(parts)
        return f"{self.algorithm} [{self.variant}] -> {self.value}{suffix}"


def require_non_negative(value: int, name: str) -> None:
    if value < 0:
        raise ValueError(f"{name} must be non-negative")
