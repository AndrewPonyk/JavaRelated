"""pytest-benchmark suite. Tracked on main only — never a PR gate
(TECH-NOTES §3.6 #10). Run: pytest benchmarks --benchmark-only
"""

import numpy as np
import pytest

from quantfinlib.options import black_scholes as bs
from quantfinlib.options import monte_carlo as mc

CHAIN = np.linspace(50, 150, 10_000)


def test_bench_batch_price_10k_chain(benchmark):
    result = benchmark(bs.price, 100.0, CHAIN, 0.2, 0.05, 1.0, "call")
    assert len(result) == CHAIN.size


def test_bench_scalar_price(benchmark):
    benchmark(bs.price, 100.0, 100.0, 0.2, 0.05, 1.0, "call")


def test_bench_implied_vol(benchmark):
    price = bs.price(100.0, 105.0, 0.25, 0.03, 0.75, "call")
    benchmark(bs.implied_vol, price, 100.0, 105.0, 0.03, 0.75, "call")


@pytest.mark.slow
def test_bench_mc_1m_paths(benchmark):
    benchmark(mc.price_european, 100.0, 100.0, 0.2, 0.05, 1.0, "call", n_paths=1_000_000, seed=42)
