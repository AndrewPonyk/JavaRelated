#pragma once
// Value-at-Risk and Expected Shortfall on a vector of portfolio returns.
// Convention: returns are simple period returns; VaR is reported as a
// POSITIVE number (a loss), confidence in (0, 1), e.g. 0.99.

#include <cstddef>

namespace qfcore {

struct VarResult {
    double var;                // e.g. 0.032 = 3.2% loss at the given confidence
    double expected_shortfall; // mean loss beyond VaR
};

// Historical (empirical-quantile) VaR/ES.
VarResult historical_var(const double* returns, std::size_t n, double confidence);

// Parametric (variance-covariance, normal) VaR/ES from sample mean/stddev.
// Portfolio-level VaR is handled by aggregating weighted returns in the
// Python layer first — mathematically identical to the w'Σw covariance form
// for the parametric method, and it captures empirical correlations for the
// historical method.
VarResult parametric_var(const double* returns, std::size_t n, double confidence);

}  // namespace qfcore
