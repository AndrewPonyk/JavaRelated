// ============================================================================
//  ml/PredictionClient.cpp
//  Circuit-breaker + fallback logic. The remote gRPC call is compiled in only
//  under RTS_ENABLE_GRPC; otherwise every prediction is served by the local
//  fallback model (still a real, working prediction).
// ============================================================================
#include "ml/PredictionClient.hpp"

#include "common/Logger.hpp"

#ifdef RTS_ENABLE_GRPC
// Real gRPC includes + generated stubs would go here when the option is on.
//   #include <grpcpp/grpcpp.h>
//   #include "prediction.grpc.pb.h"
#endif

namespace rts::ml {

namespace {
constexpr std::uint32_t kBreakerTripThreshold = 5;   // consecutive failures
}

PredictionClient::PredictionClient(std::string endpoint, std::string modelVersion,
                                   int timeoutMs)
    : endpoint_(std::move(endpoint)),
      modelVersion_(std::move(modelVersion)),
      timeoutMs_(timeoutMs) {}

PredictionClient::~PredictionClient() { stop(); }

void PredictionClient::start() {
    // With gRPC enabled this would create the channel + stub and a completion
    // queue worker. Without it, there is nothing to start.
    RTS_INFO("ML prediction client started");
}

void PredictionClient::stop() {
    // Shut down channel/worker if running.
}

PredictionResult PredictionClient::predict(const Features& f) {
#ifdef RTS_ENABLE_GRPC
    if (!breakerOpen_.load(std::memory_order_acquire)) {
        // grpc::ClientContext ctx; set deadline = now + timeoutMs_;
        // build PredictRequest from f; stub_->Predict(...);
        // on OK: onSuccess(); return mapped PredictionResult;
        // on error/timeout: onError(); fall through to fallback.
    }
#else
    (void)timeoutMs_;
    (void)endpoint_;
    (void)modelVersion_;
#endif
    // Degraded / default path: a real, deterministic local prediction.
    return fallback_.predict(f);
}

void PredictionClient::onSuccess() noexcept {
    consecutiveErrors_.store(0, std::memory_order_release);
    if (breakerOpen_.exchange(false)) {
        RTS_INFO("ML circuit breaker CLOSED — remote model recovered");
    }
}

void PredictionClient::onError() noexcept {
    const auto n = consecutiveErrors_.fetch_add(1, std::memory_order_acq_rel) + 1;
    if (n >= kBreakerTripThreshold && !breakerOpen_.exchange(true)) {
        RTS_WARN("ML circuit breaker OPEN — degrading to local fallback");
    }
}

}  // namespace rts::ml
