"""ML support tests: stable features, corpus generation, trainable baseline."""

import pytest

from sciengine.ml.corpus import generate_corpus, generate_expression
from sciengine.ml.features import PatternLabel, classify_pattern, expression_features
from sciengine.ml.model import (
    FEATURE_NAMES,
    features_matrix,
    load_model,
    predict,
    save_model,
    train_baseline,
    vectorize_features,
)


class TestFeatures:
    def test_feature_keys_match_canonical_order(self):
        features = expression_features("3x^2 + 2x - 1")
        assert sorted(features.keys()) == sorted(FEATURE_NAMES)

    def test_polynomial_features(self):
        features = expression_features("x^3 + x")
        assert features["is_polynomial"] == 1.0
        assert features["poly_degree"] == 3.0

    def test_heuristic_classification(self):
        assert classify_pattern("2x + 1").label == PatternLabel.LINEAR
        assert classify_pattern("x^2 - 4").label == PatternLabel.QUADRATIC
        assert classify_pattern("sin(x) + 1").label == PatternLabel.TRIGONOMETRIC
        assert classify_pattern("exp(2x)").label == PatternLabel.EXPONENTIAL
        assert classify_pattern("log(x) - 3").label == PatternLabel.LOGARITHMIC
        assert classify_pattern("(x+1)/(x-1)").label == PatternLabel.RATIONAL


class TestCorpus:
    def test_deterministic_per_seed(self):
        a = generate_corpus(5, seed=42)
        b = generate_corpus(5, seed=42)
        assert a == b

    def test_generated_expressions_parse(self):
        import random

        rng = random.Random(7)
        for label in (PatternLabel.LINEAR, PatternLabel.TRIGONOMETRIC, PatternLabel.RATIONAL):
            text = generate_expression(label, rng)
            expression_features(text)  # must survive the safe parser

    def test_balanced_labels(self):
        _, labels = generate_corpus(3, seed=0)
        from collections import Counter

        assert set(Counter(labels).values()) == {3}


class TestBaselineModel:
    @pytest.fixture(scope="class")
    def trained(self):
        expressions, labels = generate_corpus(40, seed=1)
        return train_baseline(expressions, labels, seed=1)

    def test_validation_accuracy_beats_chance_strongly(self, trained):
        # 7 balanced classes → chance ≈ 0.14; hand features should do far better.
        assert trained.accuracy >= 0.8

    def test_predict_known_patterns(self, trained):
        assert predict(trained, "5x + 2").label == PatternLabel.LINEAR
        assert predict(trained, "sin(3x) - 2").label == PatternLabel.TRIGONOMETRIC

    def test_save_load_round_trip(self, trained, tmp_path):
        save_model(trained, tmp_path)
        loaded = load_model(tmp_path)
        assert loaded.classes == trained.classes
        assert predict(loaded, "7x - 1").label == PatternLabel.LINEAR

    def test_vectorize_features_validates_columns(self):
        with pytest.raises(ValueError, match="Missing feature"):
            vectorize_features({"op_count": 1.0})

    def test_features_matrix_shape(self):
        matrix = features_matrix(["x + 1", "x^2"])
        assert matrix.shape == (2, len(FEATURE_NAMES))
