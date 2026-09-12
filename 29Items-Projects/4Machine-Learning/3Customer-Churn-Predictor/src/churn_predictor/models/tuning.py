"""Hyperparameter tuning with Optuna across XGBoost, LightGBM, and CatBoost.

Each algorithm gets its own search space and a pruned, cross-validated objective.
The best algorithm + params (by mean CV ROC-AUC) is returned for final training.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

import numpy as np
import optuna
import pandas as pd
from sklearn.model_selection import StratifiedKFold, cross_val_score

from churn_predictor.config import settings

logger = logging.getLogger(__name__)

# Quiet Optuna's per-trial chatter; we log our own summaries.
optuna.logging.set_verbosity(optuna.logging.WARNING)


@dataclass
class TuningResult:
    """Best configuration found across all candidate algorithms."""

    algorithm: str
    best_params: dict[str, Any]
    best_score: float
    study_per_algo: dict[str, float] = field(default_factory=dict)


def _xgb_space(trial: optuna.Trial) -> dict[str, Any]:
    return {
        "n_estimators": trial.suggest_int("n_estimators", 200, 1200, step=100),
        "max_depth": trial.suggest_int("max_depth", 3, 10),
        "learning_rate": trial.suggest_float("learning_rate", 1e-3, 0.3, log=True),
        "subsample": trial.suggest_float("subsample", 0.6, 1.0),
        "colsample_bytree": trial.suggest_float("colsample_bytree", 0.6, 1.0),
        "reg_lambda": trial.suggest_float("reg_lambda", 1e-3, 10.0, log=True),
        "scale_pos_weight": trial.suggest_float("scale_pos_weight", 1.0, 10.0),
    }


def _lgbm_space(trial: optuna.Trial) -> dict[str, Any]:
    return {
        "n_estimators": trial.suggest_int("n_estimators", 200, 1200, step=100),
        "num_leaves": trial.suggest_int("num_leaves", 15, 255),
        "learning_rate": trial.suggest_float("learning_rate", 1e-3, 0.3, log=True),
        "subsample": trial.suggest_float("subsample", 0.6, 1.0),
        "colsample_bytree": trial.suggest_float("colsample_bytree", 0.6, 1.0),
        "reg_lambda": trial.suggest_float("reg_lambda", 1e-3, 10.0, log=True),
        "class_weight": "balanced",
    }


def _catboost_space(trial: optuna.Trial) -> dict[str, Any]:
    return {
        "iterations": trial.suggest_int("iterations", 200, 1200, step=100),
        "depth": trial.suggest_int("depth", 3, 10),
        "learning_rate": trial.suggest_float("learning_rate", 1e-3, 0.3, log=True),
        "l2_leaf_reg": trial.suggest_float("l2_leaf_reg", 1.0, 10.0),
        "auto_class_weights": "Balanced",
        "verbose": False,
    }


def _build_estimator(algo: str, params: dict[str, Any]):
    """Instantiate the estimator. Imports are local to keep startup light."""
    seed = settings.random_seed
    if algo == "xgboost":
        from xgboost import XGBClassifier

        return XGBClassifier(eval_metric="auc", random_state=seed, **params)
    if algo == "lightgbm":
        from lightgbm import LGBMClassifier

        return LGBMClassifier(random_state=seed, **params)
    if algo == "catboost":
        from catboost import CatBoostClassifier

        # allow_writing_files=False prevents CatBoost from creating a 'catboost_info'
        # working directory, which fails under parallel CV / read-only filesystems.
        return CatBoostClassifier(random_seed=seed, allow_writing_files=False, **params)
    raise ValueError(f"Unknown algorithm: {algo!r}")


_SPACES = {
    "xgboost": _xgb_space,
    "lightgbm": _lgbm_space,
    "catboost": _catboost_space,
}


def _objective(trial: optuna.Trial, algo: str, X: pd.DataFrame, y: pd.Series) -> float:
    params = _SPACES[algo](trial)
    estimator = _build_estimator(algo, params)
    cv = StratifiedKFold(
        n_splits=settings.cv_folds, shuffle=True, random_state=settings.random_seed
    )
    scores = cross_val_score(estimator, X, y, scoring="roc_auc", cv=cv, n_jobs=-1)
    return float(np.mean(scores))


def tune(
    X: pd.DataFrame,
    y: pd.Series,
    *,
    algorithms: tuple[str, ...] = ("xgboost", "lightgbm", "catboost"),
    n_trials: int | None = None,
    timeout: int | None = None,
    storage_url: str | None = None,
) -> TuningResult:
    """Run an Optuna study per algorithm and return the overall best config.

    Pass ``storage_url`` (e.g. ``sqlite:///optuna_studies/churn.db``) to persist
    studies for resumable, distributed tuning; studies are resumed if they exist.
    """
    n_trials = n_trials or settings.optuna_n_trials
    timeout = timeout or settings.optuna_timeout_sec

    best: TuningResult | None = None
    per_algo: dict[str, float] = {}

    for algo in algorithms:
        study = optuna.create_study(
            study_name=f"churn-{algo}",
            direction="maximize",
            sampler=optuna.samplers.TPESampler(seed=settings.random_seed),
            pruner=optuna.pruners.MedianPruner(),
            storage=storage_url,
            load_if_exists=storage_url is not None,
        )
        study.optimize(
            lambda t, a=algo: _objective(t, a, X, y),
            n_trials=n_trials,
            timeout=timeout,
            show_progress_bar=False,
        )
        per_algo[algo] = study.best_value
        logger.info("[%s] best CV AUC=%.4f params=%s", algo, study.best_value, study.best_params)

        if best is None or study.best_value > best.best_score:
            best = TuningResult(
                algorithm=algo, best_params=study.best_params, best_score=study.best_value
            )

    assert best is not None
    best.study_per_algo = per_algo
    logger.info("Winner: %s (AUC=%.4f)", best.algorithm, best.best_score)
    return best
