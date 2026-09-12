// ============================================================================
//  ml/Predictor.hpp
//  Quant prediction abstraction. The engine depends only on IPredictor, so the
//  source can be a local model (default, dependency-free) or the remote LSTM
//  gRPC service (optional, see PredictionClient). Predictions are ADVISORY.
// ============================================================================
#pragma once

#include <cmath>
#include <cstdint>
#include <vector>

#include "common/Types.hpp"

namespace rts::ml {

struct Features {
    SymbolId           symbol{};
    Nanos              ts{0};
    std::vector<float> values;   // engineered: [return, imbalance, vol, ...]
};

struct PredictionResult {
    SymbolId symbol{};
    double   signal{0.0};        // expected short-horizon return, [-1, 1]
    double   confidence{0.0};    // [0, 1]
};

class IPredictor {
public:
    virtual ~IPredictor() = default;
    [[nodiscard]] virtual PredictionResult predict(const Features& f) = 0;
    [[nodiscard]] virtual bool ready() const noexcept { return true; }
};

// Default, in-process predictor: a deterministic momentum model. Maps the most
// recent return feature through tanh so large moves saturate. Good enough to
// demonstrate the advisory ML tilt without requiring the gRPC service; it is
// also a sane fallback when the remote model is unavailable (circuit open).
class LocalPredictor final : public IPredictor {
public:
    explicit LocalPredictor(double scale = 50.0, double confidence = 0.6)
        : scale_(scale), confidence_(confidence) {}

    [[nodiscard]] PredictionResult predict(const Features& f) override {
        const double ret = f.values.empty() ? 0.0 : static_cast<double>(f.values[0]);
        PredictionResult r{};
        r.symbol     = f.symbol;
        r.signal     = std::tanh(scale_ * ret);   // bounded to [-1, 1]
        r.confidence = confidence_;
        return r;
    }

private:
    double scale_;
    double confidence_;
};

}  // namespace rts::ml
