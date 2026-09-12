// ============================================================================
//  strategy/IStrategy.hpp
//  Strategy interface. The engine drives strategies through event callbacks;
//  strategies emit OrderIntents via the provided context (never touching the
//  network or OMS directly).
//
//  The SAME interface is driven by the live engine and the BacktestEngine, so
//  a strategy's behavior is identical in production and simulation.
// ============================================================================
#pragma once

#include "common/Types.hpp"
#include "marketdata/Tick.hpp"
#include "oms/Order.hpp"

namespace rts::strategy {

// What a strategy is allowed to do with the outside world.
class StrategyContext {
public:
    virtual ~StrategyContext() = default;

    // Submit an order intent (goes to risk -> OMS -> gateway).
    virtual void submit(const oms::OrderIntent& intent) = 0;

    // Request a cancel of a previously created order.
    virtual void cancel(OrderId id) = 0;

    // Current wall/sim time in nanoseconds.
    [[nodiscard]] virtual Nanos now() const noexcept = 0;
};

class IStrategy {
public:
    virtual ~IStrategy() = default;

    [[nodiscard]] virtual StrategyId id() const noexcept = 0;

    // Called once before the first event.
    virtual void onStart(StrategyContext&) {}

    // Top-of-book changed for a symbol this strategy follows.
    virtual void onBookUpdate(const md::BBO& bbo, StrategyContext& ctx) = 0;

    // A trade printed on the tape.
    virtual void onTrade(const md::Tick&, StrategyContext&) {}

    // One of this strategy's orders changed state.
    virtual void onFill(const oms::ExecutionReport&, StrategyContext&) {}

    // Advisory ML signal arrived (off the hot path, delivered via the ring).
    virtual void onPrediction(SymbolId, double /*signal*/, double /*confidence*/,
                              StrategyContext&) {}

    // Periodic timer (e.g. for time-based exits / requoting).
    virtual void onTimer(Nanos /*now*/, StrategyContext&) {}

    virtual void onStop() {}
};

}  // namespace rts::strategy
