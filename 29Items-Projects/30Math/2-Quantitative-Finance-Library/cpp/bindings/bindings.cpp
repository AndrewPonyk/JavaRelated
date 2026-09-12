// pybind11 module `quantfinlib._qfcore` — the ONLY file that knows both worlds.
// Rules: zero-copy NumPy in/out, GIL released around compute, C++ exceptions
// surface as Python exceptions (std::domain_error -> ValueError via pybind11's
// built-in translation; qfcore::convergence_error -> _qfcore.NativeConvergenceError,
// a RuntimeError subclass the Python facades re-wrap).

#include <pybind11/numpy.h>
#include <pybind11/pybind11.h>

#include <stdexcept>
#include <string>

#include "qfcore/american.hpp"
#include "qfcore/black_scholes.hpp"
#include "qfcore/monte_carlo.hpp"
#include "qfcore/var.hpp"

#ifndef QFCORE_VERSION
#define QFCORE_VERSION "0.0.0-dev"
#endif

namespace py = pybind11;
using namespace qfcore;

namespace {

OptionType parse_type(const std::string& kind) {
    if (kind == "call") return OptionType::Call;
    if (kind == "put") return OptionType::Put;
    throw std::domain_error("kind must be 'call' or 'put'");
}

BarrierType parse_barrier(const std::string& kind) {
    if (kind == "up-out") return BarrierType::UpOut;
    if (kind == "down-out") return BarrierType::DownOut;
    if (kind == "up-in") return BarrierType::UpIn;
    if (kind == "down-in") return BarrierType::DownIn;
    throw std::domain_error("barrier_type must be one of 'up-out', 'down-out', 'up-in', 'down-in'");
}

using DArray = py::array_t<double, py::array::c_style | py::array::forcecast>;

std::size_t common_length(const DArray& spot, const DArray& strike, const DArray& vol,
                          const DArray& rate, const DArray& expiry) {
    const auto n = static_cast<std::size_t>(spot.size());
    if (static_cast<std::size_t>(strike.size()) != n || static_cast<std::size_t>(vol.size()) != n ||
        static_cast<std::size_t>(rate.size()) != n || static_cast<std::size_t>(expiry.size()) != n) {
        throw std::domain_error("all input arrays must have equal length (broadcast in Python)");
    }
    return n;
}

py::array_t<double> price_batch(DArray spot, DArray strike, DArray vol, DArray rate,
                                DArray expiry, const std::string& kind) {
    const std::size_t n = common_length(spot, strike, vol, rate, expiry);
    const OptionType type = parse_type(kind);
    auto out = py::array_t<double>(static_cast<py::ssize_t>(n));

    const double* s = spot.data();
    const double* k = strike.data();
    const double* v = vol.data();
    const double* r = rate.data();
    const double* t = expiry.data();
    double* o = out.mutable_data();
    {
        py::gil_scoped_release release;  // pure C++ from here — no Python objects
        bs_price_batch(s, k, v, r, t, type, n, o);
    }
    return out;
}

py::dict greeks_batch(DArray spot, DArray strike, DArray vol, DArray rate, DArray expiry,
                      const std::string& kind) {
    const std::size_t n = common_length(spot, strike, vol, rate, expiry);
    const OptionType type = parse_type(kind);
    auto sn = static_cast<py::ssize_t>(n);
    py::array_t<double> delta(sn), gamma(sn), vega(sn), theta(sn), rho(sn);

    const double* s = spot.data();
    const double* k = strike.data();
    const double* v = vol.data();
    const double* r = rate.data();
    const double* t = expiry.data();
    double* pd = delta.mutable_data();
    double* pg = gamma.mutable_data();
    double* pv = vega.mutable_data();
    double* pt = theta.mutable_data();
    double* pr = rho.mutable_data();
    {
        py::gil_scoped_release release;
        bs_greeks_batch(s, k, v, r, t, type, n, pd, pg, pv, pt, pr);
    }
    return py::dict(py::arg("delta") = delta, py::arg("gamma") = gamma, py::arg("vega") = vega,
                    py::arg("theta") = theta, py::arg("rho") = rho);
}

McConfig make_config(std::size_t n_paths, std::uint64_t seed, bool antithetic) {
    McConfig cfg;
    cfg.n_paths = n_paths;
    cfg.seed = seed;
    cfg.antithetic = antithetic;
    return cfg;
}

}  // namespace

PYBIND11_MODULE(_qfcore, m) {
    m.doc() = "quantfinlib native core (C++17). Internal — use quantfinlib.* facades.";
    m.attr("__version__") = QFCORE_VERSION;

    // RuntimeError subclass carrying the no-convergence diagnostics in its message.
    py::register_exception<convergence_error>(m, "NativeConvergenceError", PyExc_RuntimeError);

    // --- Black-Scholes -------------------------------------------------------
    m.def("bs_price", [](double s, double k, double v, double r, double t, const std::string& kind) {
              return bs_price(s, k, v, r, t, parse_type(kind));
          },
          py::arg("spot"), py::arg("strike"), py::arg("vol"), py::arg("rate"),
          py::arg("expiry"), py::arg("kind"));

    m.def("bs_price_batch", &price_batch, py::arg("spot"), py::arg("strike"), py::arg("vol"),
          py::arg("rate"), py::arg("expiry"), py::arg("kind"));

    m.def("bs_greeks", [](double s, double k, double v, double r, double t, const std::string& kind) {
              const Greeks g = bs_greeks(s, k, v, r, t, parse_type(kind));
              return py::dict(py::arg("delta") = g.delta, py::arg("gamma") = g.gamma,
                              py::arg("vega") = g.vega, py::arg("theta") = g.theta,
                              py::arg("rho") = g.rho);
          },
          py::arg("spot"), py::arg("strike"), py::arg("vol"), py::arg("rate"),
          py::arg("expiry"), py::arg("kind"));

    m.def("bs_greeks_batch", &greeks_batch, py::arg("spot"), py::arg("strike"), py::arg("vol"),
          py::arg("rate"), py::arg("expiry"), py::arg("kind"));

    m.def("bs_implied_vol", [](double price, double s, double k, double r, double t,
                               const std::string& kind) {
              return bs_implied_vol(price, s, k, r, t, parse_type(kind));
          },
          py::arg("price"), py::arg("spot"), py::arg("strike"), py::arg("rate"),
          py::arg("expiry"), py::arg("kind"));

    // --- American (CRR binomial) ----------------------------------------------
    m.def("binomial_american", [](double s, double k, double v, double r, double t,
                                  const std::string& kind, std::size_t n_steps) {
              double out;
              const OptionType type = parse_type(kind);
              {
                  py::gil_scoped_release release;
                  out = binomial_american(s, k, v, r, t, type, n_steps);
              }
              return out;
          },
          py::arg("spot"), py::arg("strike"), py::arg("vol"), py::arg("rate"), py::arg("expiry"),
          py::arg("kind"), py::arg("n_steps") = 1024);

    // --- Monte Carlo ----------------------------------------------------------
    m.def("mc_price_european", [](double s, double k, double v, double r, double t,
                                  const std::string& kind, std::size_t n_paths,
                                  std::uint64_t seed, bool antithetic) {
              const OptionType type = parse_type(kind);
              const McConfig cfg = make_config(n_paths, seed, antithetic);
              McResult res;
              {
                  py::gil_scoped_release release;
                  res = mc_price_european(s, k, v, r, t, type, cfg);
              }
              return py::dict(py::arg("price") = res.price, py::arg("std_error") = res.std_error,
                              py::arg("delta") = res.delta, py::arg("vega") = res.vega,
                              py::arg("n_paths") = res.n_paths);
          },
          py::arg("spot"), py::arg("strike"), py::arg("vol"), py::arg("rate"), py::arg("expiry"),
          py::arg("kind"), py::arg("n_paths") = 100000, py::arg("seed") = 42,
          py::arg("antithetic") = true);

    m.def("mc_price_asian", [](double s, double k, double v, double r, double t,
                               const std::string& kind, std::size_t n_steps,
                               std::size_t n_paths, std::uint64_t seed, bool antithetic) {
              const OptionType type = parse_type(kind);
              const McConfig cfg = make_config(n_paths, seed, antithetic);
              McPathResult res;
              {
                  py::gil_scoped_release release;
                  res = mc_price_asian(s, k, v, r, t, type, n_steps, cfg);
              }
              return py::dict(py::arg("price") = res.price, py::arg("std_error") = res.std_error,
                              py::arg("n_paths") = res.n_paths);
          },
          py::arg("spot"), py::arg("strike"), py::arg("vol"), py::arg("rate"), py::arg("expiry"),
          py::arg("kind"), py::arg("n_steps") = 252, py::arg("n_paths") = 100000,
          py::arg("seed") = 42, py::arg("antithetic") = true);

    m.def("mc_price_barrier", [](double s, double k, double v, double r, double t,
                                 const std::string& kind, double barrier,
                                 const std::string& barrier_type, std::size_t n_steps,
                                 std::size_t n_paths, std::uint64_t seed, bool antithetic) {
              const OptionType type = parse_type(kind);
              const BarrierType btype = parse_barrier(barrier_type);
              const McConfig cfg = make_config(n_paths, seed, antithetic);
              McPathResult res;
              {
                  py::gil_scoped_release release;
                  res = mc_price_barrier(s, k, v, r, t, type, barrier, btype, n_steps, cfg);
              }
              return py::dict(py::arg("price") = res.price, py::arg("std_error") = res.std_error,
                              py::arg("n_paths") = res.n_paths);
          },
          py::arg("spot"), py::arg("strike"), py::arg("vol"), py::arg("rate"), py::arg("expiry"),
          py::arg("kind"), py::arg("barrier"), py::arg("barrier_type"), py::arg("n_steps") = 252,
          py::arg("n_paths") = 100000, py::arg("seed") = 42, py::arg("antithetic") = true);

    // --- Risk ------------------------------------------------------------------
    m.def("historical_var", [](DArray returns, double confidence) {
              const double* data = returns.data();
              const auto n = static_cast<std::size_t>(returns.size());
              VarResult res;
              {
                  py::gil_scoped_release release;
                  res = historical_var(data, n, confidence);
              }
              return py::dict(py::arg("var") = res.var,
                              py::arg("expected_shortfall") = res.expected_shortfall);
          },
          py::arg("returns"), py::arg("confidence") = 0.99);

    m.def("parametric_var", [](DArray returns, double confidence) {
              const double* data = returns.data();
              const auto n = static_cast<std::size_t>(returns.size());
              VarResult res;
              {
                  py::gil_scoped_release release;
                  res = parametric_var(data, n, confidence);
              }
              return py::dict(py::arg("var") = res.var,
                              py::arg("expected_shortfall") = res.expected_shortfall);
          },
          py::arg("returns"), py::arg("confidence") = 0.99);
}
