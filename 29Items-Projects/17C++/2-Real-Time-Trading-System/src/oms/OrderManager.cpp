// ============================================================================
//  oms/OrderManager.cpp
//  Order lifecycle implementation. The state machine is intentionally explicit:
//  illegal transitions are rejected, not silently applied — incorrect state
//  here means real money at risk.
// ============================================================================
#include "oms/OrderManager.hpp"

#include "common/Logger.hpp"

namespace rts::oms {

OrderId OrderManager::create(const OrderIntent& intent, Nanos now) {
    const auto id = static_cast<OrderId>(nextId_++);
    Order o{};
    o.id           = id;
    o.strategy     = intent.strategy;
    o.symbol       = intent.symbol;
    o.side         = intent.side;
    o.type         = intent.type;
    o.tif          = intent.tif;
    o.price        = intent.price;
    o.qty          = intent.qty;
    o.state        = OrderState::PendingNew;
    o.createdTs    = now;
    o.lastUpdateTs = now;

    live_.emplace(id, o);
    journal_(o);   // async durable record before it leaves the building
    sink_(o);      // forward to gateway (risk has already approved upstream)
    return id;
}

void OrderManager::onExecution(const ExecutionReport& er) {
    auto it = live_.find(er.orderId);
    if (it == live_.end()) {
        // Unknown/retired order: count it for reconciliation against the
        // exchange drop-copy and surface a warning. Never crash on stray reports.
        ++unknownReports_;
        RTS_WARN("exec report for unknown order",
                 static_cast<std::int64_t>(er.orderId));
        return;
    }
    Order& o = it->second;

    if (!isValidTransition(o.state, er.newState)) {
        ++rejectedTransitions_;
        RTS_ERROR("illegal order state transition",
                  static_cast<std::int64_t>(o.state),
                  static_cast<std::int64_t>(er.newState));
        return;
    }

    o.filled       = er.cumQty;
    o.state        = er.newState;
    o.lastUpdateTs = er.ts;
    journal_(o);

    if (o.isTerminal()) {
        live_.erase(it);  // free the slot; terminal state already journaled
    }
}

bool OrderManager::cancel(OrderId id, Nanos now) {
    auto it = live_.find(id);
    if (it == live_.end()) return false;
    Order& o = it->second;
    if (o.isTerminal()) return false;
    o.state        = OrderState::PendingCancel;
    o.lastUpdateTs = now;
    journal_(o);
    sink_(o);  // gateway emits a cancel request
    return true;
}

bool OrderManager::isValidTransition(OrderState from, OrderState to) const noexcept {
    using S = OrderState;
    switch (from) {
        case S::New:
        case S::PendingNew:
            return to == S::Acked || to == S::Rejected ||
                   to == S::PartiallyFilled || to == S::Filled;
        case S::Acked:
            return to == S::PartiallyFilled || to == S::Filled ||
                   to == S::PendingCancel || to == S::Cancelled;
        case S::PartiallyFilled:
            return to == S::PartiallyFilled || to == S::Filled ||
                   to == S::PendingCancel || to == S::Cancelled;
        case S::PendingCancel:
            return to == S::Cancelled || to == S::Filled ||
                   to == S::PartiallyFilled;
        default:
            return false;  // terminal states accept no transitions
    }
}

}  // namespace rts::oms
