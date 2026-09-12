// ============================================================================
//  engine/Engine.cpp
//  Pipeline orchestration and PnL bookkeeping.
// ============================================================================
#include "engine/Engine.hpp"

#include "common/Logger.hpp"
#include "fix/FixCodec.hpp"

namespace rts::engine {

Engine::Engine(config::EngineConfig cfg, persistence::ITradeRepository& repo,
               ml::IPredictor& predictor)
    : cfg_(std::move(cfg)),
      repo_(repo),
      predictor_(predictor),
      risk_(cfg_.risk),
      oms_([this](const oms::Order& o) { sendOrder(o); },
           [this](const oms::Order& o) { repo_.journalOrder(o); }),
      strategies_(*this) {
    fix::SessionConfig sc;
    sc.senderCompId  = cfg_.fix.senderCompId;
    sc.targetCompId  = cfg_.fix.targetCompId;
    sc.heartbeatSecs = cfg_.fix.heartbeatSecs;
    session_ = std::make_unique<fix::FixSession>(
        sc,
        [this](const char* d, std::size_t l) { if (orderWire_) orderWire_(d, l); },
        [this](const fix::FixMessageView& m) { onExecReport(m); });
}

void Engine::addStrategy(std::shared_ptr<strategy::IStrategy> s,
                         std::vector<SymbolId> symbols) {
    strategies_.add(std::move(s), symbols);
}

void Engine::connect() {
    session_->connect();
    strategies_.start();
    if (session_->loggedOn()) RTS_INFO("engine connected and logged on");
}

md::OrderBook& Engine::bookFor(SymbolId sym) {
    const auto key = static_cast<std::uint32_t>(sym);
    auto it = books_.find(key);
    if (it == books_.end()) it = books_.emplace(key, md::OrderBook(sym)).first;
    return it->second;
}

double Engine::midOf(SymbolId sym) const {
    auto it = lastMid_.find(static_cast<std::uint32_t>(sym));
    return it == lastMid_.end() ? 0.0 : it->second;
}

std::int64_t Engine::position(SymbolId sym) const {
    auto it = position_.find(static_cast<std::uint32_t>(sym));
    return it == position_.end() ? 0 : it->second;
}

void Engine::onTick(const md::Tick& t) {
    ++stats_.ticks;
    md::OrderBook& book = bookFor(t.symbol);
    if (!book.apply(t.side, t.price, t.qty)) return;  // top of book unchanged
    ++stats_.bookUpdates;

    const md::BBO bbo = book.topOfBook();
    if (bbo.crossed() || bbo.bidPx.ticks <= 0 || bbo.askPx.ticks <= 0) return;

    const auto key = static_cast<std::uint32_t>(t.symbol);
    const double mid = bbo.mid().toDouble();
    const double prevMid = lastMid_.count(key) ? lastMid_[key] : mid;
    const double ret = prevMid > 0.0 ? (mid - prevMid) / prevMid : 0.0;
    lastMid_[key] = mid;

    // Advisory prediction every N updates -> strategies + journal. Off-path in
    // production; here it is a real (deterministic) local model call.
    if (++bookUpdateCounter_ % static_cast<std::uint64_t>(predictEvery_) == 0 &&
        predictor_.ready()) {
        ml::Features f{};
        f.symbol = t.symbol;
        f.ts     = now();
        f.values = {static_cast<float>(ret)};
        const ml::PredictionResult pr = predictor_.predict(f);
        strategies_.dispatchPrediction(t.symbol, pr.signal, pr.confidence);
        repo_.journalPrediction(pr, now());
        ++stats_.predictions;
    }

    strategies_.dispatchBookUpdate(bbo);
}

void Engine::submit(const oms::OrderIntent& intent) {
    if (!session_->loggedOn()) {
        RTS_WARN("order submitted before session logon — dropped");
        return;
    }
    const Price refPx = Price::fromDouble(midOf(intent.symbol));
    const risk::RiskResult r = risk_.check(intent, refPx);
    if (r == risk::RiskResult::Approved) {
        oms_.create(intent, now());
    } else {
        ++stats_.rejects;
        RTS_WARN("order rejected by risk", static_cast<std::int64_t>(r));
    }
}

void Engine::cancel(OrderId id) { oms_.cancel(id, now()); }

void Engine::sendOrder(const oms::Order& o) {
    if (o.state == OrderState::PendingNew) {
        const fix::SessionIds ids{cfg_.fix.senderCompId, cfg_.fix.targetCompId,
                                  sendingTime_};
        const std::string msg = fix::FixCodec::encodeNewOrderSingle(
            o, static_cast<std::int64_t>(session_->claimSeq()), ids);
        session_->sendBytes(msg);
        ++stats_.ordersSent;
    } else if (o.state == OrderState::PendingCancel) {
        // OrderCancelRequest would be encoded here; the sample strategy uses IOC
        // orders that never rest, so this path is not exercised in the sim.
        RTS_INFO("cancel requested", static_cast<std::int64_t>(o.id));
    }
}

void Engine::onExchangeBytes(const char* data, std::size_t len) {
    session_->onBytes(data, len);
}

void Engine::onExecReport(const fix::FixMessageView& msg) {
    oms::ExecutionReport er = fix::FixCodec::decodeExecutionReport(msg);
    er.ts = now();

    // Capture symbol/side before the OMS possibly retires a terminal order.
    const oms::Order* o = oms_.find(er.orderId);
    const SymbolId sym  = o ? o->symbol : SymbolId{};
    const Side     side = o ? o->side : Side::Buy;

    oms_.onExecution(er);
    repo_.journalFill(er);
    strategies_.dispatchFill(er);

    if (er.lastQty.lots > 0) {
        const std::int64_t signedQty =
            (side == Side::Buy) ? er.lastQty.lots : -er.lastQty.lots;
        position_[static_cast<std::uint32_t>(sym)] += signedQty;
        cash_ -= static_cast<double>(signedQty) * er.lastPx.toDouble();
        risk_.onFill(sym, side, er.lastQty);
        ++stats_.fills;
        if (er.newState == OrderState::PartiallyFilled) ++stats_.partialFills;
    }
}

void Engine::onTimer(std::int64_t nowMs) { session_->onTimer(nowMs); }

double Engine::markToMarketPnl() const {
    double pnl = cash_;
    for (const auto& [key, pos] : position_) {
        auto it = lastMid_.find(key);
        if (it != lastMid_.end()) pnl += static_cast<double>(pos) * it->second;
    }
    return pnl;
}

void Engine::stop() {
    strategies_.stop();
    if (session_->loggedOn()) session_->disconnect();
    repo_.flush();
    stats_.realizedPnl = markToMarketPnl();
    RTS_INFO("engine stopped");
}

}  // namespace rts::engine
