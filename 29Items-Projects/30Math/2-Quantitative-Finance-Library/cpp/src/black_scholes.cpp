#include "qfcore/black_scholes.hpp"

#include <algorithm>
#include <cmath>
#include <sstream>
#include <stdexcept>

namespace qfcore {

void validate_option_inputs(double spot, double strike, double vol, double expiry) {
    if (!(spot > 0.0) || !std::isfinite(spot)) throw std::domain_error("spot must be > 0 and finite");
    if (!(strike > 0.0) || !std::isfinite(strike)) throw std::domain_error("strike must be > 0 and finite");
    if (vol < 0.0 || !std::isfinite(vol)) throw std::domain_error("vol must be >= 0 and finite");
    if (expiry < 0.0 || !std::isfinite(expiry)) throw std::domain_error("expiry must be >= 0 and finite");
}

namespace {

double intrinsic(double spot, double strike, OptionType type) {
    return type == OptionType::Call ? std::fmax(spot - strike, 0.0)
                                    : std::fmax(strike - spot, 0.0);
}

struct D12 {
    double d1;
    double d2;
};

D12 d12(double spot, double strike, double vol, double rate, double expiry) {
    const double sqrt_t = std::sqrt(expiry);
    const double d1 =
        (std::log(spot / strike) + (rate + 0.5 * vol * vol) * expiry) / (vol * sqrt_t);
    return {d1, d1 - vol * sqrt_t};
}

}  // namespace

double norm_cdf(double x) { return 0.5 * std::erfc(-x / std::sqrt(2.0)); }

double norm_pdf(double x) {
    static const double inv_sqrt_2pi = 0.3989422804014327;
    return inv_sqrt_2pi * std::exp(-0.5 * x * x);
}

double norm_ppf(double p) {
    if (!(p > 0.0 && p < 1.0)) throw std::domain_error("ppf argument must be in (0, 1)");
    // Acklam's rational approximation (|rel err| < 1.15e-9) ...
    static const double a[] = {-3.969683028665376e+01, 2.209460984245205e+02,
                               -2.759285104469687e+02, 1.383577518672690e+02,
                               -3.066479806614716e+01, 2.506628277459239e+00};
    static const double b[] = {-5.447609879822406e+01, 1.615858368580409e+02,
                               -1.556989798598866e+02, 6.680131188771972e+01,
                               -1.328068155288572e+01};
    static const double c[] = {-7.784894002430293e-03, -3.223964580411365e-01,
                               -2.400758277161838e+00, -2.549732539343734e+00,
                               4.374664141464968e+00,  2.938163982698783e+00};
    static const double d[] = {7.784695709041462e-03, 3.224671290700398e-01,
                               2.445134137142996e+00, 3.754408661907416e+00};
    const double p_low = 0.02425;
    double x;
    if (p < p_low) {
        const double q = std::sqrt(-2.0 * std::log(p));
        x = (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
            ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
    } else if (p <= 1.0 - p_low) {
        const double q = p - 0.5;
        const double r = q * q;
        x = (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
            (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0);
    } else {
        const double q = std::sqrt(-2.0 * std::log(1.0 - p));
        x = -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
            ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
    }
    // ... polished with one Halley step to near machine precision.
    const double e = norm_cdf(x) - p;
    const double u = e / norm_pdf(x);
    x -= u / (1.0 + 0.5 * x * u);
    return x;
}

double bs_price(double spot, double strike, double vol, double rate, double expiry,
                OptionType type) {
    validate_option_inputs(spot, strike, vol, expiry);
    // Degenerate cases collapse to (discounted) intrinsic value.
    if (expiry == 0.0 || vol == 0.0) {
        const double fwd = spot * std::exp(rate * expiry);
        const double df = std::exp(-rate * expiry);
        return df * intrinsic(fwd, strike, type);
    }
    const auto [d1, d2] = d12(spot, strike, vol, rate, expiry);
    const double df = std::exp(-rate * expiry);
    if (type == OptionType::Call) {
        return spot * norm_cdf(d1) - strike * df * norm_cdf(d2);
    }
    return strike * df * norm_cdf(-d2) - spot * norm_cdf(-d1);
}

Greeks bs_greeks(double spot, double strike, double vol, double rate, double expiry,
                 OptionType type) {
    validate_option_inputs(spot, strike, vol, expiry);

    if (expiry == 0.0 || vol == 0.0) {
        // Almost-everywhere limits. The option payoff is deterministic in the
        // forward: N(d1), N(d2) -> ITM indicator, phi(d1) -> 0.
        const double fwd = spot * std::exp(rate * expiry);
        const double df = std::exp(-rate * expiry);
        double ind;  // limit of N(d1) == N(d2)
        if (fwd > strike) {
            ind = 1.0;
        } else if (fwd < strike) {
            ind = 0.0;
        } else {
            ind = 0.5;  // exactly at the kink: symmetric convention
        }
        Greeks g{};
        g.gamma = 0.0;
        g.vega = 0.0;
        if (type == OptionType::Call) {
            g.delta = ind;
            g.theta = -rate * strike * df * ind;
            g.rho = strike * expiry * df * ind;
        } else {
            g.delta = ind - 1.0;
            g.theta = rate * strike * df * (1.0 - ind);
            g.rho = -strike * expiry * df * (1.0 - ind);
        }
        return g;
    }

    const auto [d1, d2] = d12(spot, strike, vol, rate, expiry);
    const double sqrt_t = std::sqrt(expiry);
    const double df = std::exp(-rate * expiry);
    const double pdf_d1 = norm_pdf(d1);

    Greeks g{};
    g.gamma = pdf_d1 / (spot * vol * sqrt_t);
    g.vega = spot * pdf_d1 * sqrt_t;
    if (type == OptionType::Call) {
        g.delta = norm_cdf(d1);
        g.theta = -spot * pdf_d1 * vol / (2.0 * sqrt_t) - rate * strike * df * norm_cdf(d2);
        g.rho = strike * expiry * df * norm_cdf(d2);
    } else {
        g.delta = norm_cdf(d1) - 1.0;
        g.theta = -spot * pdf_d1 * vol / (2.0 * sqrt_t) + rate * strike * df * norm_cdf(-d2);
        g.rho = -strike * expiry * df * norm_cdf(-d2);
    }
    return g;
}

void bs_price_batch(const double* spot, const double* strike, const double* vol,
                    const double* rate, const double* expiry, OptionType type,
                    std::size_t n, double* out) {
    // Called with the GIL released; must not touch Python. Throws propagate to
    // pybind11 which re-acquires the GIL and raises ValueError.
    for (std::size_t i = 0; i < n; ++i) {
        out[i] = bs_price(spot[i], strike[i], vol[i], rate[i], expiry[i], type);
    }
}

void bs_greeks_batch(const double* spot, const double* strike, const double* vol,
                     const double* rate, const double* expiry, OptionType type,
                     std::size_t n, double* delta, double* gamma, double* vega,
                     double* theta, double* rho) {
    for (std::size_t i = 0; i < n; ++i) {
        const Greeks g = bs_greeks(spot[i], strike[i], vol[i], rate[i], expiry[i], type);
        delta[i] = g.delta;
        gamma[i] = g.gamma;
        vega[i] = g.vega;
        theta[i] = g.theta;
        rho[i] = g.rho;
    }
}

double bs_implied_vol(double price, double spot, double strike, double rate, double expiry,
                      OptionType type, double lo, double hi) {
    validate_option_inputs(spot, strike, lo, expiry);
    if (!(price > 0.0) || !std::isfinite(price)) {
        throw std::domain_error("price must be > 0 and finite");
    }
    if (expiry == 0.0) throw std::domain_error("implied vol undefined at expiry == 0");
    if (!(lo < hi)) throw std::domain_error("implied vol: require lo < hi");

    // Attainability check: bs_price is strictly increasing in vol. The
    // boundary comparison carries a small tolerance — deep-ITM prices can
    // round a few ulps below intrinsic, and rejecting those would 422
    // legitimate market quotes (found by Hypothesis fuzzing).
    const double tol = 1e-12 * std::fmax(1.0, price);
    double a = lo, b = hi;
    const double f_a = bs_price(spot, strike, a, rate, expiry, type) - price;
    const double f_b = bs_price(spot, strike, b, rate, expiry, type) - price;
    if (f_a > tol || f_b < -tol) {
        throw std::domain_error("implied vol: target price outside attainable range [lo, hi]");
    }
    if (f_a >= 0.0) return a;  // price sits on the vol-floor boundary (within noise)
    if (f_b <= 0.0) return b;  // price sits on the vol-ceiling boundary

    // Newton from the Manaster-Koehler seed, hard-bracketed by [a, b]:
    // any step leaving the bracket is replaced by bisection, so convergence
    // is guaranteed monotone even for extreme moneyness.
    double sigma = std::clamp(
        std::sqrt(2.0 * std::fabs(std::log(spot / strike) + rate * expiry) / expiry), a, b);
    if (sigma == a || sigma == b) sigma = 0.5 * (a + b);

    double diff = 0.0;
    const int max_iter = 100;
    for (int iter = 1; iter <= max_iter; ++iter) {
        diff = bs_price(spot, strike, sigma, rate, expiry, type) - price;
        if (std::fabs(diff) < tol) return sigma;
        if (diff > 0.0) {
            b = sigma;
        } else {
            a = sigma;
        }
        const auto [d1, d2] = d12(spot, strike, sigma, rate, expiry);
        (void)d2;
        const double vega = spot * norm_pdf(d1) * std::sqrt(expiry);
        double next = sigma - diff / vega;  // Newton step
        if (!std::isfinite(next) || next <= a || next >= b) {
            next = 0.5 * (a + b);  // bisection safeguard
        }
        if (std::fabs(next - sigma) < 1e-16 || (b - a) < 1e-16) return next;
        sigma = next;
    }
    std::ostringstream msg;
    msg << "implied vol: no convergence after " << max_iter
        << " iterations (residual=" << std::fabs(diff) << ")";
    throw convergence_error(msg.str(), max_iter, std::fabs(diff));
}

}  // namespace qfcore
