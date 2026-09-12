// ============================================================================
//  strategy/strategies/MeanReversionStrategy.hpp
//  Sample strategy: fade short-term deviations of the mid price from a rolling
//  mean, optionally tilted by the advisory LSTM signal.
//
//  Illustrative only — NOT a profitable strategy. It demonstrates the event
//  callbacks, intent submission, and how an ML prediction is folded in without
//  ever blocking. All state is fixed-size; no allocation in the hot callbacks.
// ============================================================================
#pragma once

#include <array>

#include "common/Types.hpp"
#include "strategy/IStrategy.hpp"

namespace rts::strategy {

class MeanReversionStrategy final : public IStrategy {
public:
    MeanReversionStrategy(StrategyId id, SymbolId symbol, Qty clip)
        : id_(id), symbol_(symbol), clip_(clip) {}

    [[nodiscard]] StrategyId id() const noexcept override { return id_; }

    void onBookUpdate(const md::BBO& bbo, StrategyContext& ctx) override {
        if (bbo.crossed()) return;  // ignore degenerate snapshots
        const double mid = bbo.mid().toDouble();
        pushSample(mid);
        if (count_ < kWindow) return;  // warming up

        const double mean = rollingMean();
        if (mean <= 0.0) return;                   // degenerate prices: avoid div-by-zero
        const double dev  = (mid - mean) / mean;   // fractional deviation

        // ML tilt: positive signal biases us long, raising the fade threshold
        // for shorts and lowering it for longs. Stale/absent signal -> 0.
        const double bias = mlSignal_ * mlConfidence_ * kMlWeight;

        if (dev < -(kThreshold - bias)) {
            // price below mean -> buy (expect reversion up)
            submit(ctx, Side::Buy, bbo.askPx);
        } else if (dev > (kThreshold + bias)) {
            // price above mean -> sell
            submit(ctx, Side::Sell, bbo.bidPx);
        }
    }

    void onPrediction(SymbolId sym, double signal, double confidence,
                      StrategyContext&) override {
        if (sym != symbol_) return;
        mlSignal_     = signal;       // cached; used on next book update
        mlConfidence_ = confidence;   // never blocks; just biases thresholds
    }

    // Track signed inventory from fills on this strategy's symbol. The exec
    // report is self-describing (carries Side/Symbol), so no order map is needed.
    void onFill(const oms::ExecutionReport& er, StrategyContext&) override {
        if (er.symbol != symbol_ || er.lastQty.lots <= 0) return;
        inventory_ += (er.side == Side::Buy) ? er.lastQty.lots : -er.lastQty.lots;
    }

    [[nodiscard]] std::int64_t inventory() const noexcept { return inventory_; }

private:
    static constexpr std::size_t kWindow    = 64;
    static constexpr double      kThreshold = 0.0005;  // 5 bps deviation from mean
    static constexpr double      kMlWeight  = 0.0003;

    void pushSample(double mid) noexcept {
        sum_ -= ring_[head_];
        ring_[head_] = mid;
        sum_ += mid;
        head_ = (head_ + 1) % kWindow;
        if (count_ < kWindow) ++count_;
    }
    [[nodiscard]] double rollingMean() const noexcept {
        return sum_ / static_cast<double>(kWindow);
    }

    void submit(StrategyContext& ctx, Side side, Price px) {
        oms::OrderIntent intent{};
        intent.strategy = id_;
        intent.symbol   = symbol_;
        intent.side     = side;
        intent.type     = OrdType::Limit;
        intent.tif      = TimeInForce::IOC;
        intent.price    = px;
        intent.qty      = clip_;
        ctx.submit(intent);
    }

    StrategyId id_;
    SymbolId   symbol_;
    Qty        clip_;

    std::array<double, kWindow> ring_{};
    std::size_t head_{0};
    std::size_t count_{0};
    double      sum_{0.0};

    double mlSignal_{0.0};
    double mlConfidence_{0.0};

    std::int64_t inventory_{0};   // signed position from this strategy's fills
};

}  // namespace rts::strategy
