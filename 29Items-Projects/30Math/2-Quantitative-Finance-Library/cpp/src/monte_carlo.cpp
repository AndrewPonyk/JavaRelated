#include "qfcore/monte_carlo.hpp"

#include <cmath>
#include <cstdint>
#include <random>
#include <stdexcept>
#include <vector>

namespace qfcore {

namespace {

// Paths are simulated in fixed-size chunks; chunk c always uses the RNG
// stream derived from (seed, c). OpenMP distributes chunks across threads,
// so results are identical no matter how many threads run (or none at all).
constexpr std::size_t kChunk = 65536;

std::uint64_t splitmix64(std::uint64_t x) {
    x += 0x9E3779B97F4A7C15ULL;
    x = (x ^ (x >> 30)) * 0xBF58476D1CE4E5B9ULL;
    x = (x ^ (x >> 27)) * 0x94D049BB133111EBULL;
    return x ^ (x >> 31);
}

std::uint64_t chunk_seed(std::uint64_t seed, std::size_t chunk) {
    return splitmix64(seed ^ splitmix64(static_cast<std::uint64_t>(chunk) + 1));
}

void validate_mc(double spot, double strike, double vol, double expiry, const McConfig& config) {
    validate_option_inputs(spot, strike, vol, expiry);
    if (config.n_paths == 0) throw std::domain_error("n_paths must be > 0");
}

double payoff_of(double s, double strike, OptionType type) {
    return type == OptionType::Call ? std::fmax(s - strike, 0.0) : std::fmax(strike - s, 0.0);
}

struct Accumulators {
    double sum = 0.0;
    double sum_sq = 0.0;
    double delta_sum = 0.0;
    double vega_sum = 0.0;
};

}  // namespace

McResult mc_price_european(double spot, double strike, double vol, double rate, double expiry,
                           OptionType type, const McConfig& config) {
    validate_mc(spot, strike, vol, expiry, config);

    // European payoff under GBM: simulate S_T exactly, no time-stepping.
    //   S_T = S0 * exp((r - 0.5*sigma^2)*T + sigma*sqrt(T)*Z)
    const double drift = (rate - 0.5 * vol * vol) * expiry;
    const double diffusion = vol * std::sqrt(expiry);
    const double df = std::exp(-rate * expiry);
    const double sign = type == OptionType::Call ? 1.0 : -1.0;
    const std::size_t n = config.n_paths;
    const auto n_chunks = static_cast<long long>((n + kChunk - 1) / kChunk);

    double sum = 0.0, sum_sq = 0.0, delta_sum = 0.0, vega_sum = 0.0;

    // Pathwise sensitivities (a.e. differentiable payoff):
    //   dPayoff/dS0   = 1{ITM} * sign * S_T / S0
    //   dPayoff/dvol  = 1{ITM} * sign * S_T * (ln(S_T/S0) - (r + vol^2/2)T) / vol
    // vol==0 makes the vega estimator singular; report 0 (price is intrinsic).
    const double vega_coeff = (rate + 0.5 * vol * vol) * expiry;

#if defined(QFCORE_HAS_OPENMP)
#pragma omp parallel for schedule(static) reduction(+ : sum, sum_sq, delta_sum, vega_sum)
#endif
    for (long long c = 0; c < n_chunks; ++c) {
        std::mt19937_64 rng(chunk_seed(config.seed, static_cast<std::size_t>(c)));
        std::normal_distribution<double> normal(0.0, 1.0);
        const std::size_t begin = static_cast<std::size_t>(c) * kChunk;
        const std::size_t count = std::min(kChunk, n - begin);
        Accumulators acc;

        for (std::size_t i = 0; i < count; ++i) {
            const double z = normal(rng);
            double payoff = 0.0, delta = 0.0, vega = 0.0;
            const int reps = config.antithetic ? 2 : 1;
            for (int r2 = 0; r2 < reps; ++r2) {
                const double zz = r2 == 0 ? z : -z;
                const double st = spot * std::exp(drift + diffusion * zz);
                const double p = payoff_of(st, strike, type);
                payoff += p;
                if (p > 0.0) {
                    delta += sign * st / spot;
                    if (vol > 0.0) {
                        vega += sign * st * (std::log(st / spot) - vega_coeff) / vol;
                    }
                }
            }
            const double inv = 1.0 / reps;
            payoff *= inv;
            acc.sum += payoff;
            acc.sum_sq += payoff * payoff;
            acc.delta_sum += delta * inv;
            acc.vega_sum += vega * inv;
        }
        sum += acc.sum;
        sum_sq += acc.sum_sq;
        delta_sum += acc.delta_sum;
        vega_sum += acc.vega_sum;
    }

    const auto dn = static_cast<double>(n);
    const double mean = sum / dn;
    const double variance = std::fmax(sum_sq / dn - mean * mean, 0.0);
    McResult r{};
    r.price = df * mean;
    r.std_error = df * std::sqrt(variance / dn);
    r.delta = df * delta_sum / dn;
    r.vega = df * vega_sum / dn;
    r.n_paths = n;
    return r;
}

McPathResult mc_price_asian(double spot, double strike, double vol, double rate, double expiry,
                            OptionType type, std::size_t n_steps, const McConfig& config) {
    validate_mc(spot, strike, vol, expiry, config);
    if (n_steps == 0) throw std::domain_error("n_steps must be > 0");
    if (expiry == 0.0) {
        const double p = payoff_of(spot, strike, type);
        return {p, 0.0, config.n_paths};
    }

    const double dt = expiry / static_cast<double>(n_steps);
    const double drift = (rate - 0.5 * vol * vol) * dt;
    const double diffusion = vol * std::sqrt(dt);
    const double df = std::exp(-rate * expiry);
    const std::size_t n = config.n_paths;
    const auto n_chunks = static_cast<long long>((n + kChunk - 1) / kChunk);

    double sum = 0.0, sum_sq = 0.0;

#if defined(QFCORE_HAS_OPENMP)
#pragma omp parallel for schedule(static) reduction(+ : sum, sum_sq)
#endif
    for (long long c = 0; c < n_chunks; ++c) {
        std::mt19937_64 rng(chunk_seed(config.seed, static_cast<std::size_t>(c)));
        std::normal_distribution<double> normal(0.0, 1.0);
        const std::size_t begin = static_cast<std::size_t>(c) * kChunk;
        const std::size_t count = std::min(kChunk, n - begin);
        std::vector<double> zs(n_steps);
        double local_sum = 0.0, local_sq = 0.0;

        for (std::size_t i = 0; i < count; ++i) {
            for (std::size_t s = 0; s < n_steps; ++s) zs[s] = normal(rng);
            double payoff = 0.0;
            const int reps = config.antithetic ? 2 : 1;
            for (int r2 = 0; r2 < reps; ++r2) {
                const double flip = r2 == 0 ? 1.0 : -1.0;
                double st = spot;
                double running = 0.0;
                for (std::size_t s = 0; s < n_steps; ++s) {
                    st *= std::exp(drift + diffusion * flip * zs[s]);
                    running += st;
                }
                payoff += payoff_of(running / static_cast<double>(n_steps), strike, type);
            }
            payoff /= reps;
            local_sum += payoff;
            local_sq += payoff * payoff;
        }
        sum += local_sum;
        sum_sq += local_sq;
    }

    const auto dn = static_cast<double>(n);
    const double mean = sum / dn;
    const double variance = std::fmax(sum_sq / dn - mean * mean, 0.0);
    return {df * mean, df * std::sqrt(variance / dn), n};
}

McPathResult mc_price_barrier(double spot, double strike, double vol, double rate, double expiry,
                              OptionType type, double barrier, BarrierType barrier_type,
                              std::size_t n_steps, const McConfig& config) {
    validate_mc(spot, strike, vol, expiry, config);
    if (n_steps == 0) throw std::domain_error("n_steps must be > 0");
    if (!(barrier > 0.0) || !std::isfinite(barrier)) {
        throw std::domain_error("barrier must be > 0 and finite");
    }

    const bool up = barrier_type == BarrierType::UpOut || barrier_type == BarrierType::UpIn;
    const bool knock_out =
        barrier_type == BarrierType::UpOut || barrier_type == BarrierType::DownOut;
    // Inception counts as a monitoring date: born-crossed paths are knocked.
    const bool crossed_at_start = up ? spot >= barrier : spot <= barrier;

    const double dt = expiry / static_cast<double>(n_steps);
    const double drift = (rate - 0.5 * vol * vol) * dt;
    const double diffusion = vol * std::sqrt(dt);
    const double df = std::exp(-rate * expiry);
    const std::size_t n = config.n_paths;
    const auto n_chunks = static_cast<long long>((n + kChunk - 1) / kChunk);

    double sum = 0.0, sum_sq = 0.0;

#if defined(QFCORE_HAS_OPENMP)
#pragma omp parallel for schedule(static) reduction(+ : sum, sum_sq)
#endif
    for (long long c = 0; c < n_chunks; ++c) {
        std::mt19937_64 rng(chunk_seed(config.seed, static_cast<std::size_t>(c)));
        std::normal_distribution<double> normal(0.0, 1.0);
        const std::size_t begin = static_cast<std::size_t>(c) * kChunk;
        const std::size_t count = std::min(kChunk, n - begin);
        std::vector<double> zs(n_steps);
        double local_sum = 0.0, local_sq = 0.0;

        for (std::size_t i = 0; i < count; ++i) {
            for (std::size_t s = 0; s < n_steps; ++s) zs[s] = normal(rng);
            double payoff = 0.0;
            const int reps = config.antithetic ? 2 : 1;
            for (int r2 = 0; r2 < reps; ++r2) {
                const double flip = r2 == 0 ? 1.0 : -1.0;
                double st = spot;
                bool crossed = crossed_at_start;
                for (std::size_t s = 0; s < n_steps; ++s) {
                    st *= std::exp(drift + diffusion * flip * zs[s]);
                    if (up ? st >= barrier : st <= barrier) crossed = true;
                }
                const bool alive = knock_out ? !crossed : crossed;
                if (alive) payoff += payoff_of(st, strike, type);
            }
            payoff /= reps;
            local_sum += payoff;
            local_sq += payoff * payoff;
        }
        sum += local_sum;
        sum_sq += local_sq;
    }

    const auto dn = static_cast<double>(n);
    const double mean = sum / dn;
    const double variance = std::fmax(sum_sq / dn - mean * mean, 0.0);
    return {df * mean, df * std::sqrt(variance / dn), n};
}

}  // namespace qfcore
