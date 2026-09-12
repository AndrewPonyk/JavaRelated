// ============================================================================
//  oms/OrderManager.hpp
//  Owns the lifecycle of every live order: id assignment, state transitions,
//  client<->exchange id mapping, and reconciliation against execution reports.
//
//  The OMS is the single authority on order state. Strategies submit intents;
//  the OMS validates the transition, persists (async), and forwards approved
//  orders to risk -> gateway.
// ============================================================================
#pragma once

#include <cstdint>
#include <functional>
#include <unordered_map>

#include "common/Types.hpp"
#include "oms/Order.hpp"

namespace rts::oms {

// Sink for orders that have passed the OMS and (next) risk: encode + send.
using OrderSink = std::function<void(const Order&)>;
// Async journal sink (off hot path).
using JournalSink = std::function<void(const Order&)>;

class OrderManager {
public:
    OrderManager(OrderSink sink, JournalSink journal)
        : sink_(std::move(sink)), journal_(std::move(journal)) {
        live_.reserve(4096);
    }

    // Create an order from a strategy intent. Returns the new order id.
    OrderId create(const OrderIntent& intent, Nanos now);

    // Apply an execution report from the exchange; updates state + fills.
    void onExecution(const ExecutionReport& er);

    // Request cancel of a live order.
    bool cancel(OrderId id, Nanos now);

    [[nodiscard]] const Order* find(OrderId id) const noexcept {
        auto it = live_.find(id);
        return it == live_.end() ? nullptr : &it->second;
    }
    [[nodiscard]] std::size_t liveCount() const noexcept { return live_.size(); }

    // Reports received for orders not in the live set (e.g. for an order already
    // retired, or one this instance never sent) — surfaced for reconciliation
    // against the exchange drop-copy / monitoring.
    [[nodiscard]] std::uint64_t unknownReports() const noexcept { return unknownReports_; }
    [[nodiscard]] std::uint64_t rejectedTransitions() const noexcept {
        return rejectedTransitions_;
    }

private:
    bool isValidTransition(OrderState from, OrderState to) const noexcept;

    OrderSink   sink_;
    JournalSink journal_;
    std::unordered_map<OrderId, Order> live_;
    std::uint64_t nextId_{1};
    std::uint64_t unknownReports_{0};
    std::uint64_t rejectedTransitions_{0};
};

}  // namespace rts::oms
