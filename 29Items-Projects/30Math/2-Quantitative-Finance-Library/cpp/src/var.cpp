#include "qfcore/var.hpp"

#include <algorithm>
#include <cmath>
#include <numeric>
#include <stdexcept>
#include <vector>

#include "qfcore/black_scholes.hpp"  // norm_ppf / norm_pdf

namespace qfcore {

namespace {

void validate(std::size_t n, double confidence) {
    if (n < 2) throw std::domain_error("need at least 2 returns");
    if (!(confidence > 0.0 && confidence < 1.0)) {
        throw std::domain_error("confidence must be in (0, 1)");
    }
}

}  // namespace

VarResult historical_var(const double* returns, std::size_t n, double confidence) {
    validate(n, confidence);
    std::vector<double> sorted(returns, returns + n);
    std::sort(sorted.begin(), sorted.end());  // ascending: worst losses first

    // Linear-interpolated empirical quantile — matches numpy.quantile(..., "linear"),
    // so the Python fallback and the native core agree exactly.
    const double pos = (1.0 - confidence) * static_cast<double>(n - 1);
    const auto lo_idx = static_cast<std::size_t>(std::floor(pos));
    const std::size_t hi_idx = std::min(lo_idx + 1, n - 1);
    const double frac = pos - static_cast<double>(lo_idx);
    const double quantile = sorted[lo_idx] + frac * (sorted[hi_idx] - sorted[lo_idx]);

    // ES: mean of returns at or below the VaR quantile (the empirical tail).
    double tail_sum = 0.0;
    std::size_t tail_count = 0;
    for (std::size_t i = 0; i < n && sorted[i] <= quantile; ++i) {
        tail_sum += sorted[i];
        ++tail_count;
    }

    VarResult r{};
    r.var = -quantile;
    r.expected_shortfall =
        tail_count > 0 ? -(tail_sum / static_cast<double>(tail_count)) : r.var;
    return r;
}

VarResult parametric_var(const double* returns, std::size_t n, double confidence) {
    validate(n, confidence);
    const double mean = std::accumulate(returns, returns + n, 0.0) / static_cast<double>(n);
    double ss = 0.0;
    for (std::size_t i = 0; i < n; ++i) {
        const double d = returns[i] - mean;
        ss += d * d;
    }
    const double stddev = std::sqrt(ss / static_cast<double>(n - 1));

    const double z = norm_ppf(1.0 - confidence);  // negative for confidence > 0.5
    VarResult r{};
    r.var = -(mean + z * stddev);
    // Normal ES: -(mu - sigma * pdf(z) / (1 - confidence))
    r.expected_shortfall = -(mean - stddev * norm_pdf(z) / (1.0 - confidence));
    return r;
}

}  // namespace qfcore
