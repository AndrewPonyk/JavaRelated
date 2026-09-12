// ============================================================================
//  exchange/SimulatedExchange.hpp
//  An in-process FIX acceptor standing in for a real venue. It maintains a book
//  per symbol from the market-data feed, accepts NewOrderSingle messages from
//  the engine, matches them against the touch, and returns ExecutionReports —
//  all over the real FIX codec. The "wire" is a byte callback, so this same
//  component sits behind a TCP socket in the networked build with no changes.
// ============================================================================
#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>

#include "fix/FixSession.hpp"      // fix::WireSink
#include "marketdata/OrderBook.hpp"
#include "marketdata/Tick.hpp"
#include "oms/Order.hpp"

namespace rts::exchange {

class SimulatedExchange {
public:
    SimulatedExchange(std::string compId, std::string targetCompId);

    // Where execution reports / admin replies are written (engine's onBytes).
    void setWire(fix::WireSink toEngine) { toEngine_ = std::move(toEngine); }

    // Market data also flows to the exchange so it can match at the touch.
    void onTick(const md::Tick& t);

    // Inbound FIX bytes from the engine.
    void onBytes(const char* data, std::size_t len);

    [[nodiscard]] std::uint64_t ordersReceived() const noexcept { return ordersReceived_; }
    [[nodiscard]] std::uint64_t execsSent()      const noexcept { return execsSent_; }
    [[nodiscard]] bool          loggedOn()       const noexcept { return loggedOn_; }

private:
    md::OrderBook& bookFor(SymbolId sym);
    void handleNewOrder(const fix::FixMessageView& msg);
    void handleLogon();
    void sendLogon();
    void sendExec(const oms::Order& order, const oms::ExecutionReport& er);
    void reply(const std::string& framed);

    std::string   compId_;
    std::string   targetCompId_;
    fix::WireSink toEngine_;

    std::unordered_map<std::uint32_t, md::OrderBook> books_;
    std::uint64_t outSeq_{1};
    std::uint64_t execId_{1};
    std::uint64_t ordersReceived_{0};
    std::uint64_t execsSent_{0};
    bool          loggedOn_{false};
    std::string   rxBuffer_;
};

}  // namespace rts::exchange
