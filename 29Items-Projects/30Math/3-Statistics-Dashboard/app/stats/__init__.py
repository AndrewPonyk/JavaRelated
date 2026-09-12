"""Statistics engine: pure functions over pandas/NumPy/SciPy/statsmodels.

Layering rule: nothing in this package imports Streamlit, SQLAlchemy, or Plotly.
DataFrames/arrays in, frozen dataclasses out — no I/O, no side effects.
"""
