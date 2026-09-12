#pragma once
// Black-Scholes closed-form pricing and Greeks.
// Contract: inputs are validated here (throws std::domain_error); functions
// never return NaN — they either return a finite value or throw.

#include <cstddef>

#include "qfcore/exceptions.hpp"

namespace qfcore {

enum class OptionType { Call, Put };

struct Greeks {
    double delta;
    double gamma;
    double vega;   // per 1.0 of vol (not per 1%)
    double theta;  // per year (calendar decay, dV/dt)
    double rho;    // per 1.0 of rate
};

// Standard normal CDF/PDF/inverse-CDF used across the library.
double norm_cdf(double x);
double norm_pdf(double x);
double norm_ppf(double p);  // Acklam approximation + one Halley refinement (~1e-15)

// Price a European option. spot, strike > 0; vol, expiry >= 0.
// expiry==0 or vol==0 collapse to (discounted) intrinsic value.
double bs_price(double spot, double strike, double vol, double rate, double expiry,
                OptionType type);

// All first-order Greeks + gamma in one pass (shares d1/d2 computation).
// At expiry==0 or vol==0 the almost-everywhere limit values are returned
// (delta = ITM indicator, 0.5 exactly at the kink; gamma/vega -> 0).
Greeks bs_greeks(double spot, double strike, double vol, double rate, double expiry,
                 OptionType type);

// Batch interfaces used by the Python bindings. All array arguments have
// length n; output buffers are preallocated by the caller.
void bs_price_batch(const double* spot, const double* strike, const double* vol,
                    const double* rate, const double* expiry, OptionType type,
                    std::size_t n, double* out);

void bs_greeks_batch(const double* spot, const double* strike, const double* vol,
                     const double* rate, const double* expiry, OptionType type,
                     std::size_t n, double* delta, double* gamma, double* vega,
                     double* theta, double* rho);

// Implied volatility from a target price. Newton iteration seeded with the
// Manaster-Koehler point, hard-bracketed by bisection so it cannot escape
// [lo, hi]. Throws std::domain_error when the price is not attainable for
// any vol in [lo, hi], qfcore::convergence_error (with iteration/residual
// diagnostics) if iteration fails to converge.
double bs_implied_vol(double price, double spot, double strike, double rate, double expiry,
                      OptionType type, double lo = 1e-6, double hi = 5.0);

}  // namespace qfcore
