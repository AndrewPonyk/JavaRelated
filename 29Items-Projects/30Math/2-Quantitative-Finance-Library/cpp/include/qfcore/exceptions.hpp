#pragma once
// Error contract of the native core (see ARCHITECTURE.md §2.6):
//   std::domain_error      — caller's inputs are wrong  → Python ValueError
//   qfcore::convergence_error — numerics failed, carries diagnostics
//                                                       → Python RuntimeError subclass
// Functions either return finite values or throw; they never return NaN.

#include <stdexcept>
#include <string>

namespace qfcore {

class convergence_error : public std::runtime_error {
  public:
    convergence_error(const std::string& msg, int iterations_, double residual_)
        : std::runtime_error(msg), iterations(iterations_), residual(residual_) {}

    int iterations;
    double residual;
};

// Shared input guard used by every pricing entry point.
void validate_option_inputs(double spot, double strike, double vol, double expiry);

}  // namespace qfcore
