"""ML volatility-surface fitting. Requires the [ml] extra (torch).

Torch is imported lazily so `import quantfinlib` stays cheap and works
without the extra installed.
"""

from quantfinlib.ml.vol_surface import VolSurfaceFitter, VolSurfaceModel

__all__ = ["VolSurfaceFitter", "VolSurfaceModel"]
