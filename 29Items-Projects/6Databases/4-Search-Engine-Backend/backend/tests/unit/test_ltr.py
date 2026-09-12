"""LTR rescore clause builders: plugin vs native vs off."""

from app.search.ltr import build_ltr_rescore


def test_plugin_mode_builds_sltr_rescore() -> None:
    rescore = build_ltr_rescore(
        mode="plugin", model_name="m1", query_text="tv stand", window_size=100
    )
    assert rescore is not None
    assert rescore["window_size"] == 100
    sltr = rescore["query"]["rescore_query"]["sltr"]
    assert sltr["model"] == "m1"
    assert sltr["params"] == {"keywords": "tv stand"}
    assert rescore["query"]["score_mode"] == "total"


def test_native_mode_builds_learning_to_rank_rescore() -> None:
    rescore = build_ltr_rescore(mode="native", model_name="m2", query_text="tv", window_size=50)
    assert rescore == {
        "window_size": 50,
        "learning_to_rank": {"model_id": "m2", "params": {"keywords": "tv"}},
    }


def test_off_and_unknown_modes_fail_safe_to_none() -> None:
    assert build_ltr_rescore(mode="off", model_name="m", query_text="q", window_size=10) is None
    assert build_ltr_rescore(mode="bogus", model_name="m", query_text="q", window_size=10) is None
