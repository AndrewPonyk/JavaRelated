#pragma once
// Monte Carlo pricing engine under GBM.
//
// Determinism: paths are generated in fixed-size chunks, each chunk with its
// own RNG stream derived from (seed, chunk index). Results are therefore
// bit-identical for a given seed regardless of thread count (OpenMP splits
// work across chunks, never inside them). Cross-platform reproducibility is
// best-effort (std::normal_distribution is implementation-defined).

#include <cstddef>
#include <cstdint>

#include "qfcore/black_scholes.hpp"

namespace qfcore {

struct McConfig {
    std::size_t n_paths = 100000;
    std::uint64_t seed = 42;
    bool antithetic = true;
};

// European result carries pathwise sensitivity estimates (delta, vega) —
// they share the terminal-value simulation, so they are nearly free.
struct McResult {
    double price;
    double std_error;     // standard error of the price estimator
    double delta;         // pathwise estimator
    double vega;          // pathwise estimator
    std::size_t n_paths;  // effective paths used
};

// Path-dependent results (no pathwise Greeks: payoffs are not a.e. smooth
// in the same simple way; finite differences with common random numbers are
// the supported route at the Python layer).
struct McPathResult {
    double price;
    double std_error;
    std::size_t n_paths;
};

enum class BarrierType { UpOut, DownOut, UpIn, DownIn };

// European option: terminal spot simulated exactly in one step (no
// discretization bias).
McResult mc_price_european(double spot, double strike, double vol, double rate, double expiry,
                           OptionType type, const McConfig& config);

// Arithmetic-average Asian option, average observed at n_steps equally
// spaced monitoring dates in (0, T].
McPathResult mc_price_asian(double spot, double strike, double vol, double rate, double expiry,
                            OptionType type, std::size_t n_steps, const McConfig& config);

// Discretely monitored barrier option (n_steps monitoring dates in (0, T],
// plus the inception spot). Knock-in/out against `barrier`.
McPathResult mc_price_barrier(double spot, double strike, double vol, double rate, double expiry,
                              OptionType type, double barrier, BarrierType barrier_type,
                              std::size_t n_steps, const McConfig& config);

}  // namespace qfcore
