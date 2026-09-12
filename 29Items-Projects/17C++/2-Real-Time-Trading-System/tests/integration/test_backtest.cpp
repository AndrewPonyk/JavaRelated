// ============================================================================
//  tests/integration/test_backtest.cpp
//  Backtest harness: produces fills and is deterministic across runs (the
//  property that underpins backtest/live parity).
// ============================================================================
#include "backtest/BacktestEngine.hpp"
#include "marketdata/MarketDataSource.hpp"
#include "strategy/strategies/MeanReversionStrategy.hpp"

#include <gtest/gtest.h>

#include <memory>

using namespace rts;

namespace {
std::shared_ptr<strategy::IStrategy> makeStrategy() {
    return std::make_shared<strategy::MeanReversionStrategy>(
        static_cast<StrategyId>(1), static_cast<SymbolId>(1), Qty{100});
}
}  // namespace

TEST(Backtest, RunsAndProducesFills) {
    backtest::BacktestEngine bt(makeStrategy(), static_cast<SymbolId>(1));
    const auto ticks = md::generateSynthetic(static_cast<SymbolId>(1), 3000, 7);
    const auto r = bt.runTicks(ticks);

    EXPECT_GT(r.ticks, 0u);
    EXPECT_GT(r.orders, 0);
    EXPECT_GT(r.fills, 0);
    EXPECT_GE(r.maxDrawdown, 0.0);
}

TEST(Backtest, DeterministicAcrossRuns) {
    const auto ticks = md::generateSynthetic(static_cast<SymbolId>(1), 2000, 123);

    backtest::BacktestEngine a(makeStrategy(), static_cast<SymbolId>(1));
    backtest::BacktestEngine b(makeStrategy(), static_cast<SymbolId>(1));
    const auto ra = a.runTicks(ticks);
    const auto rb = b.runTicks(ticks);

    EXPECT_EQ(ra.orders, rb.orders);
    EXPECT_EQ(ra.fills, rb.fills);
    EXPECT_NEAR(ra.realizedPnl, rb.realizedPnl, 1e-9);
}
