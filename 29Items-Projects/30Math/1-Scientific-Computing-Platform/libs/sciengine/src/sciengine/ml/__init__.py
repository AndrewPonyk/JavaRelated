"""ML support: expression featurization and the heuristic pattern classifier.

Feature extraction lives HERE (not in ml/training) so that SageMaker training
and the API-side fallback use byte-identical features — see docs/TECH-NOTES.md
§3.6 on training/serving skew.
"""
