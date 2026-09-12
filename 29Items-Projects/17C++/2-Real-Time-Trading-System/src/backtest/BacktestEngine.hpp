// ============================================================================
//  backtest/BacktestEngine.hpp
//  Event-driven historical replay. Feeds recorded/synthetic ticks through the
//  SAME OrderBook and strategy code used live; only the data source (file) and
//  clock (simulated) differ. A pluggable FillModel simulates venue fills so PnL
//  and drawdown can be computed without a live exchange — this is what gives
//  backtest/live parity.
// ============================================================================
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "core/time/Clock.hpp"
#include "marketdata/OrderBook.hpp"
#include "marketdata/Tick.hpp"
#include "oms/Order.hpp"
#include "strategy/IStrategy.hpp"

namespace rts::backtest {

struct BacktestResult {
    std::int64_t orders{0};
    std::int64_t fills{0};
    double       realizedPnl{0.0};    // mark-to-market at the final mid
    double       maxDrawdown{0.0};
    std::int64_t finalPosition{0};
    std::uint64_t ticks{0};
};

// Decides whether/how a strategy intent fills against the replayed book.
class FillModel {
public:
    virtual ~FillModel() = default;
    virtual bool tryFill(const oms::OrderIntent& intent, const md::BBO& bbo,
                         Nanos now, oms::ExecutionReport& er) = 0;
};

// Marketable-at-touch model: mirrors the SimulatedExchange so a strategy fills
// identically in backtest and live-replay.
class TouchFillModel final : public FillModel {
public:
    bool tryFill(const oms::OrderIntent& intent, const md::BBO& bbo, Nanos now,
                 oms::ExecutionReport& er) override;
};

class BacktestEngine {
public:
    BacktestEngine(std::shared_ptr<strategy::IStrategy> strat, SymbolId symbol);

    void setFillModel(std::unique_ptr<FillModel> fm) { fillModel_ = std::move(fm); }

    BacktestResult run(const std::string& tickFilePath);          // reads CSV
    BacktestResult runTicks(const std::vector<md::Tick>& ticks);  // in-memory

private:
    void onTick(const md::Tick& t);

    std::shared_ptr<strategy::IStrategy> strategy_;
    md::OrderBook                        book_;
    SymbolId                             symbol_;
    rts::time::SimulatedClock            clock_;
    std::unique_ptr<FillModel>           fillModel_;
    BacktestResult                       result_{};

    double        cash_{0.0};
    std::int64_t  position_{0};
    double        lastMid_{0.0};
    double        peakEquity_{0.0};
    std::uint64_t orderIdSeq_{0};
};

}  // namespace rts::backtest
