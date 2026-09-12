#pragma once
// American option pricing via the Cox-Ross-Rubinstein binomial lattice.
// Convergence is O(1/n_steps); 1024 steps prices to ~1e-3 on typical inputs.

#include <cstddef>

#include "qfcore/black_scholes.hpp"

namespace qfcore {

// Price an American option (no dividends). Degenerate cases (vol==0 or
// expiry==0) return the exact optimal-exercise value in closed form.
double binomial_american(double spot, double strike, double vol, double rate, double expiry,
                         OptionType type, std::size_t n_steps = 1024);

}  // namespace qfcore
