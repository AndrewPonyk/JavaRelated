"""Two-tier model registry.

Tier 1 - MLflow (:class:`MLflowRegistry`): the registry of record when the
tracking server is reachable and the ``mlflow`` package is installed.

Tier 2 - local filesystem store (:class:`LocalModelStore`): a fully
functional, versioned registry under ``settings.model_dir`` that the
service falls back to whenever MLflow is unavailable. Training always
writes to the local store, so serving, promotion, drift baselines and the
A/B experiment work end-to-end with zero external dependencies.

Local store layout::

    <model_dir>/registry.json                     alias -> version mapping + version index
    <model_dir>/v<N>/model.joblib                 pickled sklearn Pipeline
    <model_dir>/v<N>/metadata.json                metrics, feature names, provenance
    <model_dir>/v<N>/baseline.json                per-feature PSI baseline histograms
"""

from __future__ import annotations

import json
import os
import threading
import time
import uuid
from pathlib import Path
from typing import Any

import joblib

from fraud_detection.core.config import get_settings

ALIASES = ("champion", "challenger")

# One process-wide lock for registry mutations: retraining threads and API
# handlers each construct their own LocalModelStore, and on Windows a racy
# os.replace against a file another thread is reading fails with a sharing
# violation. All instances must therefore serialize on the same lock.
_REGISTRY_LOCK = threading.RLock()


class ModelRegistryUnavailable(RuntimeError):
    """Raised when the MLflow registry cannot be used (not installed/reachable)."""


class LocalModelStore:
    """Versioned model artifacts + alias mapping on the local filesystem."""

    def __init__(self, model_dir: str | Path | None = None) -> None:
        self._dir = Path(model_dir if model_dir is not None else get_settings().model_dir)
        self._lock = _REGISTRY_LOCK

    @property
    def model_dir(self) -> Path:
        """Root directory of the store."""
        return self._dir

    def _registry_path(self) -> Path:
        return self._dir / "registry.json"

    def read_registry(self) -> dict[str, Any]:
        """Return the registry document (empty skeleton when absent)."""
        path = self._registry_path()
        if not path.exists():
            return {"aliases": {}, "versions": {}}
        with path.open(encoding="utf-8") as handle:
            registry: dict[str, Any] = json.load(handle)
        return registry

    def _write_registry(self, registry: dict[str, Any]) -> None:
        """Atomically replace registry.json (write temp file + os.replace).

        The replace is retried briefly: on Windows a concurrent reader
        holding the destination open makes os.replace fail with a sharing
        violation (PermissionError) even under the process-wide lock.
        """
        self._dir.mkdir(parents=True, exist_ok=True)
        tmp = self._dir / f".registry-{uuid.uuid4().hex}.tmp"
        tmp.write_text(json.dumps(registry, indent=2, sort_keys=True), encoding="utf-8")
        last_error: PermissionError | None = None
        for _ in range(20):
            try:
                os.replace(tmp, self._registry_path())
                return
            except PermissionError as exc:
                last_error = exc
                time.sleep(0.05)
        raise last_error if last_error else RuntimeError("registry write failed")

    def next_version(self) -> str:
        """Return the next free numeric version string."""
        versions = self.read_registry()["versions"]
        return str(max((int(v) for v in versions), default=0) + 1)

    def save_version(
        self, pipeline: Any, metadata: dict[str, Any], baseline: dict[str, Any]
    ) -> str:
        """Persist a trained pipeline as a new version and update aliases.

        The new version always becomes the ``challenger``; it also becomes
        the ``champion`` when no champion exists yet (first ever model).

        Args:
            pipeline: Fitted sklearn Pipeline.
            metadata: Provenance and metrics (must contain ``metrics`` and
                ``feature_names`` keys).
            baseline: Per-feature PSI baseline histograms.

        Returns:
            The new version string.
        """
        with self._lock:
            registry = self.read_registry()
            version = str(max((int(v) for v in registry["versions"]), default=0) + 1)
            version_dir = self._dir / f"v{version}"
            version_dir.mkdir(parents=True, exist_ok=True)

            joblib.dump(pipeline, version_dir / "model.joblib")
            metadata = {**metadata, "version": version}
            (version_dir / "metadata.json").write_text(
                json.dumps(metadata, indent=2, sort_keys=True, default=str), encoding="utf-8"
            )
            (version_dir / "baseline.json").write_text(
                json.dumps(baseline, indent=2, sort_keys=True), encoding="utf-8"
            )

            metrics = metadata.get("metrics", {})
            registry["versions"][version] = {
                "path": f"v{version}/model.joblib",
                "auc": metrics.get("auc"),
                "recall": metrics.get("recall"),
                "trained_at": str(metadata.get("trained_at", "")),
                "feature_names": metadata.get("feature_names", []),
            }
            registry["aliases"]["challenger"] = version
            registry["aliases"].setdefault("champion", version)
            self._write_registry(registry)
            return version

    def resolve_version(self, alias_or_version: str) -> str | None:
        """Resolve an alias (or pass through a version) to a version string."""
        registry = self.read_registry()
        if alias_or_version in registry["aliases"]:
            resolved: str | None = registry["aliases"][alias_or_version]
            return resolved
        if alias_or_version in registry["versions"]:
            return alias_or_version
        return None

    def load(self, alias_or_version: str) -> tuple[Any, dict[str, Any]]:
        """Load a pipeline and its metadata by alias or version.

        Raises:
            KeyError: If the alias/version is unknown.
        """
        version = self.resolve_version(alias_or_version)
        if version is None:
            raise KeyError(f"unknown model alias or version: {alias_or_version!r}")
        version_dir = self._dir / f"v{version}"
        pipeline = joblib.load(version_dir / "model.joblib")
        with (version_dir / "metadata.json").open(encoding="utf-8") as handle:
            metadata = json.load(handle)
        return pipeline, metadata

    def load_baseline(self, alias_or_version: str) -> dict[str, Any] | None:
        """Return the PSI baseline for an alias/version, or None when absent."""
        version = self.resolve_version(alias_or_version)
        if version is None:
            return None
        path = self._dir / f"v{version}" / "baseline.json"
        if not path.exists():
            return None
        with path.open(encoding="utf-8") as handle:
            baseline: dict[str, Any] = json.load(handle)
        return baseline

    def get_aliases(self) -> dict[str, str | None]:
        """Return the current alias -> version mapping (always both keys)."""
        aliases = self.read_registry()["aliases"]
        return {alias: aliases.get(alias) for alias in ALIASES}

    def list_versions(self) -> list[dict[str, Any]]:
        """Return all versions (newest first) with their registry index entries."""
        registry = self.read_registry()
        stage_by_version = {v: alias for alias, v in registry["aliases"].items()}
        entries = []
        for version, info in registry["versions"].items():
            entries.append(
                {
                    "version": version,
                    "stage": stage_by_version.get(version, "archived"),
                    **info,
                }
            )
        entries.sort(key=lambda entry: int(entry["version"]), reverse=True)
        return entries

    def set_alias(self, alias: str, version: str) -> str | None:
        """Point ``alias`` at ``version``; returns the previously aliased version.

        Raises:
            ValueError: For unknown aliases.
            KeyError: For unknown versions.
        """
        if alias not in ALIASES:
            raise ValueError(f"unknown alias {alias!r}; expected one of {ALIASES}")
        with self._lock:
            registry = self.read_registry()
            if version not in registry["versions"]:
                raise KeyError(f"unknown model version: {version!r}")
            previous: str | None = registry["aliases"].get(alias)
            registry["aliases"][alias] = version
            self._write_registry(registry)
            return previous

    def delete_version(self, version: str) -> None:
        """Remove a version's artifacts and registry entry.

        Raises:
            KeyError: If the version does not exist.
            ValueError: If the version is the current champion (protected).
        """
        with self._lock:
            registry = self.read_registry()
            if version not in registry["versions"]:
                raise KeyError(f"unknown model version: {version!r}")
            if registry["aliases"].get("champion") == version:
                raise ValueError("cannot delete the current champion version")
            registry["versions"].pop(version)
            registry["aliases"] = {
                alias: v for alias, v in registry["aliases"].items() if v != version
            }
            self._write_registry(registry)
        version_dir = self._dir / f"v{version}"
        if version_dir.exists():
            for child in sorted(version_dir.glob("**/*"), reverse=True):
                child.unlink()
            version_dir.rmdir()


class MLflowRegistry:
    """Alias-based promotion helper for the MLflow model registry."""

    def __init__(self, tracking_uri: str | None = None, model_name: str | None = None) -> None:
        settings = get_settings()
        self._tracking_uri = tracking_uri or settings.mlflow_tracking_uri
        self._model_name = model_name or settings.model_name

    @staticmethod
    def available() -> bool:
        """Return True when the mlflow package is importable."""
        try:
            import mlflow  # noqa: F401, PLC0415 - deliberate lazy import

            return True
        except ImportError:
            return False

    def _client(self) -> Any:
        """Return an ``MlflowClient``, or raise if mlflow is unavailable."""
        try:
            import mlflow  # noqa: PLC0415 - deliberate lazy import
            from mlflow.tracking import MlflowClient  # noqa: PLC0415
        except ImportError as exc:
            raise ModelRegistryUnavailable(
                "mlflow is not installed; the local model store is serving instead"
            ) from exc
        mlflow.set_tracking_uri(self._tracking_uri)
        return MlflowClient(tracking_uri=self._tracking_uri)

    def get_version(self, alias: str) -> str:
        """Return the model version currently bound to ``alias``.

        Raises:
            ModelRegistryUnavailable: If mlflow is not installed.
        """
        client = self._client()
        model_version = client.get_model_version_by_alias(self._model_name, alias)
        return str(model_version.version)

    def promote(self, version: str, alias: str) -> None:
        """Point ``alias`` at ``version`` (e.g. promote challenger to champion).

        The corresponding ``model_versions`` DB row is updated by the API
        route layer, which is the single writer for catalog state.

        Raises:
            ModelRegistryUnavailable: If mlflow is not installed.
        """
        client = self._client()
        client.set_registered_model_alias(self._model_name, alias, version)
