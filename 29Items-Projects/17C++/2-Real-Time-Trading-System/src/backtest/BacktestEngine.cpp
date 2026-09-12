// ============================================================================
//  backtest/BacktestEngine.cpp
//  Replay loop + default fill model + PnL/drawdown accounting.
// ============================================================================
#include "backtest/BacktestEngine.hpp"

#include <algorithm>

#include "common/Logger.hpp"
#include "marketdata/MarketDataSource.hpp"
#include "strategy/IStrategy.hpp"

namespace rts::backtest {

namespace {
// StrategyContext for backtests: captures emitted intents; time is simulated.
class BacktestContext final : public strategy::StrategyContext {
public:
    explicit BacktestContext(rts::time::SimulatedClock& clock) : clock_(clock) {}
    void submit(const oms::OrderIntent& intent) override { pending_.push_back(intent); }
    void cancel(OrderId) override {}
    [[nodiscard]] Nanos now() const noexcept override { return clock_.now(); }

    std::vector<oms::OrderIntent> pending_;

private:
    rts::time::SimulatedClock& clock_;
};
}  // namespace

bool TouchFillModel::tryFill(const oms::OrderIntent& intent, const md::BBO& bbo,
                             Nanos now, oms::ExecutionReport& er) {
    std::int64_t avail = 0;
    Price        px{};
    if (intent.side == Side::Buy) {
        if (bbo.askQty.lots > 0 &&
            (intent.type == OrdType::Market || intent.price >= bbo.askPx)) {
            avail = bbo.askQty.lots;
            px    = bbo.askPx;
        }
    } else {
        if (bbo.bidQty.lots > 0 &&
            (intent.type == OrdType::Market || intent.price <= bbo.bidPx)) {
            avail = bbo.bidQty.lots;
            px    = bbo.bidPx;
        }
    }
    if (avail <= 0) return false;

    const std::int64_t q = std::min(intent.qty.lots, avail);
    er.lastQty  = Qty{q};
    er.lastPx   = px;
    er.cumQty   = Qty{q};
    er.newState = (q == intent.qty.lots) ? OrderState::Filled
                                         : OrderState::PartiallyFilled;
    er.ts       = now;
    er.symbol   = intent.symbol;
    er.side     = intent.side;
    return true;
}

BacktestEngine::BacktestEngine(std::shared_ptr<strategy::IStrategy> strat,
                               SymbolId symbol)
    : strategy_(std::move(strat)),
      book_(symbol),
      symbol_(symbol),
      fillModel_(std::make_unique<TouchFillModel>()) {}

BacktestResult BacktestEngine::run(const std::string& tickFilePath) {
    return runTicks(md::readCsv(tickFilePath));
}

BacktestResult BacktestEngine::runTicks(const std::vector<md::Tick>& ticks) {
    RTS_INFO("backtest starting", static_cast<std::int64_t>(ticks.size()));
    for (const auto& t : ticks) {
        clock_.set(t.exchangeTs);
        onTick(t);
        ++result_.ticks;
    }
    result_.realizedPnl   = cash_ + static_cast<double>(position_) * lastMid_;
    result_.finalPosition = position_;
    RTS_INFO("backtest complete");
    return result_;
}

void BacktestEngine::onTick(const md::Tick& t) {
    if (!book_.apply(t.side, t.price, t.qty)) return;  // top of book unchanged

    const md::BBO bbo = book_.topOfBook();
    if (bbo.crossed() || bbo.bidPx.ticks <= 0 || bbo.askPx.ticks <= 0) return;
    lastMid_ = bbo.mid().toDouble();

    BacktestContext ctx(clock_);
    strategy_->onBookUpdate(bbo, ctx);

    for (const auto& intent : ctx.pending_) {
        ++result_.orders;
        oms::ExecutionReport er{};
        er.orderId = static_cast<OrderId>(++orderIdSeq_);
        if (fillModel_->tryFill(intent, bbo, clock_.now(), er)) {
            ++result_.fills;
            const std::int64_t signedQty =
                (intent.side == Side::Buy) ? er.lastQty.lots : -er.lastQty.lots;
            position_ += signedQty;
            cash_     -= static_cast<double>(signedQty) * er.lastPx.toDouble();
            strategy_->onFill(er, ctx);

            const double equity = cash_ + static_cast<double>(position_) * lastMid_;
            peakEquity_ = std::max(peakEquity_, equity);
            result_.maxDrawdown = std::max(result_.maxDrawdown, peakEquity_ - equity);
        }
    }
}

}  // namespace rts::backtest
