// ============================================================================
//  engine/Engine.hpp
//  The orchestrator. Owns the per-symbol books, risk engine, OMS, strategy
//  engine, FIX session, and predictor, and wires them into the pipeline:
//
//     tick -> book -> (prediction) -> strategy -> risk -> OMS -> FIX -> wire
//     wire -> FIX -> execution report -> OMS / strategy / risk / journal / PnL
//
//  Implements StrategyContext so strategies submit intents back through it. The
//  "wire" is a byte sink, so the same Engine drives the in-process simulated
//  exchange today and a real TCP/FIX gateway in the networked build.
// ============================================================================
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "common/Config.hpp"
#include "core/time/Clock.hpp"
#include "fix/FixSession.hpp"
#include "marketdata/OrderBook.hpp"
#include "marketdata/Tick.hpp"
#include "ml/Predictor.hpp"
#include "oms/OrderManager.hpp"
#include "persistence/ITradeRepository.hpp"
#include "risk/RiskEngine.hpp"
#include "strategy/IStrategy.hpp"
#include "strategy/StrategyEngine.hpp"

namespace rts::engine {

struct EngineStats {
    std::uint64_t ticks{0};
    std::uint64_t bookUpdates{0};
    std::uint64_t ordersSent{0};
    std::uint64_t fills{0};
    std::uint64_t partialFills{0};
    std::uint64_t rejects{0};
    std::uint64_t predictions{0};
    double        realizedPnl{0.0};
};

class Engine final : public strategy::StrategyContext {
public:
    Engine(config::EngineConfig cfg, persistence::ITradeRepository& repo,
           ml::IPredictor& predictor);

    void addStrategy(std::shared_ptr<strategy::IStrategy> s,
                     std::vector<SymbolId> symbols);

    // Wiring to the venue (in-process exchange or real gateway).
    void setOrderWire(fix::WireSink toExchange) { orderWire_ = std::move(toExchange); }
    void onExchangeBytes(const char* data, std::size_t len);

    void connect();                       // FIX logon
    void onTick(const md::Tick& t);       // market data in
    void onTimer(std::int64_t nowMs);     // heartbeats
    void stop();

    // ---- StrategyContext --------------------------------------------------
    void submit(const oms::OrderIntent& intent) override;
    void cancel(OrderId id) override;
    [[nodiscard]] Nanos now() const noexcept override { return clock_.now(); }

    [[nodiscard]] EngineStats   stats()         const noexcept { return stats_; }
    [[nodiscard]] double        markToMarketPnl() const;
    [[nodiscard]] bool          loggedOn()      const noexcept {
        return session_ && session_->loggedOn();
    }
    [[nodiscard]] std::int64_t  position(SymbolId sym) const;

private:
    void onExecReport(const fix::FixMessageView& msg);
    void sendOrder(const oms::Order& o);            // OMS sink
    md::OrderBook& bookFor(SymbolId sym);
    [[nodiscard]] double midOf(SymbolId sym) const;

    config::EngineConfig            cfg_;
    persistence::ITradeRepository&  repo_;
    ml::IPredictor&                 predictor_;
    rts::time::SteadyClock          clock_;

    risk::RiskEngine                risk_;
    oms::OrderManager               oms_;
    strategy::StrategyEngine        strategies_;
    std::unique_ptr<fix::FixSession> session_;
    fix::WireSink                   orderWire_;
    std::string                     sendingTime_{"20250101-00:00:00.000"};

    std::unordered_map<std::uint32_t, md::OrderBook>   books_;
    std::unordered_map<std::uint32_t, double>          lastMid_;
    std::unordered_map<std::uint32_t, std::int64_t>    position_;
    double                                             cash_{0.0};

    EngineStats   stats_{};
    std::uint64_t bookUpdateCounter_{0};
    int           predictEvery_{16};   // emit an advisory prediction every N updates
};

}  // namespace rts::engine
