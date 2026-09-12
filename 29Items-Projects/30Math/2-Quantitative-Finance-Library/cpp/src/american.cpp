#include "qfcore/american.hpp"

#include <algorithm>
#include <cmath>
#include <stdexcept>
#include <vector>

namespace qfcore {

double binomial_american(double spot, double strike, double vol, double rate, double expiry,
                         OptionType type, std::size_t n_steps) {
    validate_option_inputs(spot, strike, vol, expiry);
    if (n_steps == 0) throw std::domain_error("n_steps must be > 0");

    const double intrinsic_now = type == OptionType::Call ? std::fmax(spot - strike, 0.0)
                                                          : std::fmax(strike - spot, 0.0);
    if (expiry == 0.0) return intrinsic_now;
    if (vol == 0.0) {
        // Deterministic path: the holder exercises at the single best time.
        // Call (r >= 0): waiting dominates -> European degenerate value.
        // Put  (r >= 0): discounting erodes K -> exercise immediately if ITM.
        const double df = std::exp(-rate * expiry);
        const double fwd = spot * std::exp(rate * expiry);
        const double european = df * (type == OptionType::Call ? std::fmax(fwd - strike, 0.0)
                                                               : std::fmax(strike - fwd, 0.0));
        return std::fmax(intrinsic_now, european);
    }

    // Cox-Ross-Rubinstein lattice.
    const double dt = expiry / static_cast<double>(n_steps);
    const double u = std::exp(vol * std::sqrt(dt));
    const double d = 1.0 / u;
    const double growth = std::exp(rate * dt);
    const double p = (growth - d) / (u - d);
    if (!(p > 0.0 && p < 1.0)) {
        // |r|*sqrt(dt) exceeds vol: refine the lattice instead of mispricing.
        throw std::domain_error(
            "binomial lattice unstable for these parameters: increase n_steps "
            "(need vol > |rate|*sqrt(T/n_steps))");
    }
    const double disc = 1.0 / growth;

    // Terminal payoffs at nodes j = 0..n (j up-moves).
    std::vector<double> values(n_steps + 1);
    for (std::size_t j = 0; j <= n_steps; ++j) {
        const double st = spot * std::pow(u, static_cast<double>(2.0 * j) - static_cast<double>(n_steps));
        values[j] = type == OptionType::Call ? std::fmax(st - strike, 0.0)
                                             : std::fmax(strike - st, 0.0);
    }

    // Backward induction with the early-exercise comparison at every node.
    for (std::size_t step = n_steps; step-- > 0;) {
        for (std::size_t j = 0; j <= step; ++j) {
            const double cont = disc * (p * values[j + 1] + (1.0 - p) * values[j]);
            const double st =
                spot * std::pow(u, static_cast<double>(2.0 * j) - static_cast<double>(step));
            const double exercise = type == OptionType::Call ? std::fmax(st - strike, 0.0)
                                                             : std::fmax(strike - st, 0.0);
            values[j] = std::fmax(cont, exercise);
        }
    }
    return values[0];
}

}  // namespace qfcore
