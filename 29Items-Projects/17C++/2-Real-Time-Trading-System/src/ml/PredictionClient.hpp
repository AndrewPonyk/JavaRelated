// ============================================================================
//  ml/PredictionClient.hpp
//  IPredictor backed by the remote LSTM gRPC service, with a circuit breaker
//  and a local fallback so a slow/dead model degrades to rules-only behavior
//  instead of ever stalling the engine.
//
//  The gRPC wire is compiled only when RTS_ENABLE_GRPC is defined (optional
//  dependency). Without it, predict() transparently uses the local fallback,
//  which keeps the default build dependency-free and fully functional.
// ============================================================================
#pragma once

#include <atomic>
#include <cstdint>
#include <string>

#include "ml/Predictor.hpp"

namespace rts::ml {

class PredictionClient final : public IPredictor {
public:
    PredictionClient(std::string endpoint, std::string modelVersion, int timeoutMs);
    ~PredictionClient() override;

    void start();
    void stop();

    [[nodiscard]] PredictionResult predict(const Features& f) override;
    [[nodiscard]] bool ready() const noexcept override {
        return !breakerOpen_.load(std::memory_order_acquire);
    }

    [[nodiscard]] bool breakerOpen() const noexcept {
        return breakerOpen_.load(std::memory_order_acquire);
    }
    [[nodiscard]] std::uint32_t consecutiveErrors() const noexcept {
        return consecutiveErrors_.load(std::memory_order_acquire);
    }

private:
    void onSuccess() noexcept;
    void onError() noexcept;   // trips the breaker after a threshold

    std::string   endpoint_;
    std::string   modelVersion_;
    int           timeoutMs_;

    std::atomic<bool>          breakerOpen_{false};
    std::atomic<std::uint32_t> consecutiveErrors_{0};
    LocalPredictor             fallback_;
};

}  // namespace rts::ml
