#include <cmath>

#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_floating_point.hpp>

#include "qfcore/american.hpp"
#include "qfcore/black_scholes.hpp"
#include "qfcore/monte_carlo.hpp"
#include "qfcore/var.hpp"

using namespace qfcore;
using Catch::Matchers::WithinAbs;
using Catch::Matchers::WithinRel;

// Reference value: Hull, "Options, Futures and Other Derivatives",
// S=42, K=40, r=0.10, sigma=0.20, T=0.5 → call = 4.759, put = 0.8086.
TEST_CASE("bs_price matches Hull reference values", "[black_scholes]") {
    CHECK_THAT(bs_price(42, 40, 0.20, 0.10, 0.5, OptionType::Call), WithinAbs(4.759, 5e-4));
    CHECK_THAT(bs_price(42, 40, 0.20, 0.10, 0.5, OptionType::Put), WithinAbs(0.8086, 5e-4));
}

TEST_CASE("put-call parity holds", "[black_scholes]") {
    const double s = 100, k = 95, v = 0.3, r = 0.05, t = 1.25;
    const double call = bs_price(s, k, v, r, t, OptionType::Call);
    const double put = bs_price(s, k, v, r, t, OptionType::Put);
    CHECK_THAT(call - put, WithinAbs(s - k * std::exp(-r * t), 1e-12));
}

TEST_CASE("deep ITM/OTM prices behave", "[black_scholes]") {
    // Deep ITM call ~ discounted forward intrinsic; deep OTM ~ 0.
    const double itm = bs_price(100, 1, 0.2, 0.05, 1.0, OptionType::Call);
    CHECK_THAT(itm, WithinRel(100 - std::exp(-0.05), 1e-9));
    CHECK(bs_price(100, 10000, 0.2, 0.05, 1.0, OptionType::Call) < 1e-10);
}

TEST_CASE("invalid inputs throw, never NaN", "[black_scholes]") {
    CHECK_THROWS(bs_price(-1, 100, 0.2, 0.05, 1.0, OptionType::Call));
    CHECK_THROWS(bs_price(100, 100, -0.2, 0.05, 1.0, OptionType::Call));
    CHECK_THROWS(bs_price(100, 100, 0.2, 0.05, -1.0, OptionType::Call));
}

TEST_CASE("greeks agree with central finite differences", "[black_scholes]") {
    const double s = 100, k = 105, v = 0.25, r = 0.03, t = 0.75;
    const Greeks g = bs_greeks(s, k, v, r, t, OptionType::Call);
    const double h = 1e-5;
    const double fd_delta = (bs_price(s + h, k, v, r, t, OptionType::Call) -
                             bs_price(s - h, k, v, r, t, OptionType::Call)) / (2 * h);
    const double fd_vega = (bs_price(s, k, v + h, r, t, OptionType::Call) -
                            bs_price(s, k, v - h, r, t, OptionType::Call)) / (2 * h);
    const double fd_rho = (bs_price(s, k, v, r + h, t, OptionType::Call) -
                           bs_price(s, k, v, r - h, t, OptionType::Call)) / (2 * h);
    const double fd_theta = -(bs_price(s, k, v, r, t + h, OptionType::Call) -
                              bs_price(s, k, v, r, t - h, OptionType::Call)) / (2 * h);
    CHECK_THAT(g.delta, WithinRel(fd_delta, 1e-6));
    CHECK_THAT(g.vega, WithinRel(fd_vega, 1e-6));
    CHECK_THAT(g.rho, WithinRel(fd_rho, 1e-6));
    CHECK_THAT(g.theta, WithinRel(fd_theta, 1e-5));
}

TEST_CASE("greeks limit values at expiry and zero vol", "[black_scholes]") {
    const Greeks itm = bs_greeks(110, 100, 0.2, 0.05, 0.0, OptionType::Call);
    CHECK(itm.delta == 1.0);
    CHECK(itm.gamma == 0.0);
    CHECK(itm.vega == 0.0);
    const Greeks atm = bs_greeks(100, 100, 0.2, 0.05, 0.0, OptionType::Call);
    CHECK(atm.delta == 0.5);
    const Greeks zero_vol = bs_greeks(100, 90, 0.0, 0.05, 1.0, OptionType::Call);
    CHECK(zero_vol.delta == 1.0);  // forward ITM
}

TEST_CASE("implied vol round-trips a known price", "[black_scholes]") {
    const double vol = 0.27;
    const double price = bs_price(100, 105, vol, 0.03, 0.75, OptionType::Call);
    const double iv = bs_implied_vol(price, 100, 105, 0.03, 0.75, OptionType::Call);
    CHECK_THAT(iv, WithinAbs(vol, 1e-8));
}

TEST_CASE("implied vol rejects unattainable prices", "[black_scholes]") {
    CHECK_THROWS_AS(bs_implied_vol(1e-9, 100, 100, 0.05, 1.0, OptionType::Call),
                    std::domain_error);
}

TEST_CASE("MC converges to closed form within 3 standard errors", "[monte_carlo]") {
    McConfig cfg;
    cfg.n_paths = 200000;
    cfg.seed = 7;
    const auto mc = mc_price_european(100, 100, 0.2, 0.05, 1.0, OptionType::Call, cfg);
    const double bs = bs_price(100, 100, 0.2, 0.05, 1.0, OptionType::Call);
    CHECK(std::fabs(mc.price - bs) < 3.0 * mc.std_error);
}

TEST_CASE("antithetic variates reduce variance", "[monte_carlo]") {
    McConfig plain;
    plain.n_paths = 100000;
    plain.seed = 1;
    plain.antithetic = false;
    McConfig anti = plain;
    anti.antithetic = true;
    const auto p = mc_price_european(100, 100, 0.2, 0.05, 1.0, OptionType::Call, plain);
    const auto a = mc_price_european(100, 100, 0.2, 0.05, 1.0, OptionType::Call, anti);
    CHECK(a.std_error < p.std_error);
}

TEST_CASE("pathwise greeks track closed form", "[monte_carlo]") {
    McConfig cfg;
    cfg.n_paths = 500000;
    cfg.seed = 11;
    const auto mc = mc_price_european(100, 100, 0.2, 0.05, 1.0, OptionType::Call, cfg);
    const Greeks g = bs_greeks(100, 100, 0.2, 0.05, 1.0, OptionType::Call);
    CHECK_THAT(mc.delta, WithinAbs(g.delta, 5e-3));
    CHECK_THAT(mc.vega, WithinRel(g.vega, 2e-2));
}

TEST_CASE("barrier in-out parity is exact per path", "[monte_carlo]") {
    McConfig cfg;
    cfg.n_paths = 50000;
    cfg.seed = 21;
    const auto ko = mc_price_barrier(100, 100, 0.2, 0.05, 1.0, OptionType::Call, 130.0,
                                     BarrierType::UpOut, 50, cfg);
    const auto ki = mc_price_barrier(100, 100, 0.2, 0.05, 1.0, OptionType::Call, 130.0,
                                     BarrierType::UpIn, 50, cfg);
    const auto vanilla = mc_price_barrier(100, 100, 0.2, 0.05, 1.0, OptionType::Call, 1e12,
                                          BarrierType::UpOut, 50, cfg);
    CHECK_THAT(ko.price + ki.price, WithinAbs(vanilla.price, 1e-10));
}

TEST_CASE("asian option is cheaper than european", "[monte_carlo]") {
    McConfig cfg;
    cfg.n_paths = 100000;
    cfg.seed = 5;
    const auto asian = mc_price_asian(100, 100, 0.2, 0.05, 1.0, OptionType::Call, 50, cfg);
    CHECK(asian.price < bs_price(100, 100, 0.2, 0.05, 1.0, OptionType::Call));
}

TEST_CASE("american call equals european, put carries premium", "[american]") {
    const double amer_call = binomial_american(100, 100, 0.2, 0.05, 1.0, OptionType::Call, 2000);
    CHECK_THAT(amer_call, WithinAbs(bs_price(100, 100, 0.2, 0.05, 1.0, OptionType::Call), 5e-3));
    const double amer_put = binomial_american(100, 110, 0.2, 0.05, 1.0, OptionType::Put, 2000);
    CHECK(amer_put > bs_price(100, 110, 0.2, 0.05, 1.0, OptionType::Put));
    CHECK(binomial_american(50, 100, 0.2, 0.05, 1.0, OptionType::Put, 500) >= 50.0 - 1e-12);
}

TEST_CASE("VaR golden values on a known sample", "[var]") {
    // 10 sorted returns; 90% historical VaR = -quantile(0.10).
    // pos = 0.1 * 9 = 0.9 -> lerp between sorted[0] and sorted[1].
    const double returns[] = {-0.05, -0.03, -0.01, 0.0, 0.005, 0.01, 0.015, 0.02, 0.03, 0.04};
    const auto hist = historical_var(returns, 10, 0.90);
    CHECK_THAT(hist.var, WithinAbs(0.05 - 0.9 * (0.05 - 0.03), 1e-12));  // 0.032
    CHECK(hist.expected_shortfall >= hist.var);
    const auto para = parametric_var(returns, 10, 0.99);
    CHECK(para.var > 0.0);
    CHECK(para.expected_shortfall > para.var);
}

TEST_CASE("VaR input validation", "[var]") {
    const double one[] = {0.01};
    CHECK_THROWS(historical_var(one, 1, 0.99));
    const double two[] = {0.01, -0.01};
    CHECK_THROWS(historical_var(two, 2, 1.5));
}
