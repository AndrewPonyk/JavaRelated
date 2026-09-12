"""Thread-safe runtime store for the A/B experiment configuration.

Seeded lazily from settings; updated at runtime through
``PUT /api/v1/models/ab-config`` so experiments can be tuned without a
redeploy. State is per-process (each replica converges on the next
config-map rollout; runtime updates are an operational override).
"""

from __future__ import annotations

import threading

from fraud_detection.core.config import get_settings


class ABConfigStore:
    """Holds the effective ``enabled``/``traffic_split`` pair."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._enabled: bool | None = None
        self._traffic_split: int | None = None

    def get(self) -> tuple[bool, int]:
        """Return the effective (enabled, traffic_split) configuration."""
        with self._lock:
            if self._enabled is None or self._traffic_split is None:
                settings = get_settings()
                if self._enabled is None:
                    self._enabled = settings.ab_test_enabled
                if self._traffic_split is None:
                    self._traffic_split = settings.ab_traffic_split
            return self._enabled, self._traffic_split

    def update(
        self, enabled: bool | None = None, traffic_split: int | None = None
    ) -> tuple[bool, int]:
        """Apply a runtime override; ``None`` fields are left unchanged.

        Args:
            enabled: New experiment on/off state.
            traffic_split: New challenger traffic percentage (0-100).

        Returns:
            The effective configuration after the update.

        Raises:
            ValueError: If ``traffic_split`` is outside 0-100.
        """
        if traffic_split is not None and not 0 <= traffic_split <= 100:
            raise ValueError("traffic_split must be between 0 and 100")
        current_enabled, current_split = self.get()
        with self._lock:
            self._enabled = current_enabled if enabled is None else enabled
            self._traffic_split = current_split if traffic_split is None else traffic_split
            return self._enabled, self._traffic_split

    def reset(self) -> None:
        """Forget runtime overrides (re-seed from settings on next read)."""
        with self._lock:
            self._enabled = None
            self._traffic_split = None
