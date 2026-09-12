// ============================================================================
//  strategy/StrategyEngine.hpp
//  Dispatches market-data and order events to registered strategies. Maintains
//  a symbol -> subscribers index so a tick only wakes interested strategies.
//
//  Runs on a pinned strategy core; consumes from the disruptor and fans events
//  to strategies, which emit intents back through the StrategyContext.
// ============================================================================
#pragma once

#include <memory>
#include <unordered_map>
#include <vector>

#include "common/Types.hpp"
#include "strategy/IStrategy.hpp"

namespace rts::strategy {

class StrategyEngine {
public:
    explicit StrategyEngine(StrategyContext& ctx) : ctx_(ctx) {}

    // Register a strategy and the symbols it cares about.
    void add(std::shared_ptr<IStrategy> strat, const std::vector<SymbolId>& symbols) {
        strategies_.push_back(strat);
        for (auto s : symbols) subscribers_[s].push_back(strat.get());
    }

    void start() {
        for (auto& s : strategies_) s->onStart(ctx_);
    }
    void stop() {
        for (auto& s : strategies_) s->onStop();
    }

    // --- Event entry points (called by the disruptor consumer) -------------
    void dispatchBookUpdate(const md::BBO& bbo) {
        auto it = subscribers_.find(bbo.symbol);
        if (it == subscribers_.end()) return;
        for (auto* s : it->second) s->onBookUpdate(bbo, ctx_);
    }

    void dispatchTrade(const md::Tick& t) {
        auto it = subscribers_.find(t.symbol);
        if (it == subscribers_.end()) return;
        for (auto* s : it->second) s->onTrade(t, ctx_);
    }

    void dispatchPrediction(SymbolId sym, double signal, double conf) {
        auto it = subscribers_.find(sym);
        if (it == subscribers_.end()) return;
        for (auto* s : it->second) s->onPrediction(sym, signal, conf, ctx_);
    }

    void dispatchTimer(Nanos now) {
        for (auto& s : strategies_) s->onTimer(now, ctx_);
    }

    // Execution reports carry no symbol; fan out to every strategy, which
    // filters by the orders it owns.
    void dispatchFill(const oms::ExecutionReport& er) {
        for (auto& s : strategies_) s->onFill(er, ctx_);
    }

private:
    StrategyContext& ctx_;
    std::vector<std::shared_ptr<IStrategy>> strategies_;
    std::unordered_map<SymbolId, std::vector<IStrategy*>> subscribers_;
};

}  // namespace rts::strategy
