// ============================================================================
//  oms/Order.hpp
//  Order and ExecutionReport PODs. These flow through pools and the disruptor,
//  so they stay trivially copyable and pointer-free.
// ============================================================================
#pragma once

#include <cstdint>

#include "common/Types.hpp"

namespace rts::oms {

struct Order {
    OrderId      id{};
    StrategyId   strategy{};
    SymbolId     symbol{};
    Side         side{Side::Buy};
    OrdType      type{OrdType::Limit};
    TimeInForce  tif{TimeInForce::Day};
    Price        price{};
    Qty          qty{};
    Qty          filled{};
    OrderState   state{OrderState::New};
    Nanos        createdTs{0};
    Nanos        lastUpdateTs{0};

    [[nodiscard]] Qty leaves() const noexcept {
        return Qty{qty.lots - filled.lots};
    }
    [[nodiscard]] bool isTerminal() const noexcept {
        return state == OrderState::Filled ||
               state == OrderState::Cancelled ||
               state == OrderState::Rejected;
    }
};

// An intent emitted by a strategy; the OMS turns it into an Order.
struct OrderIntent {
    StrategyId strategy{};
    SymbolId   symbol{};
    Side       side{Side::Buy};
    OrdType    type{OrdType::Limit};
    TimeInForce tif{TimeInForce::Day};
    Price      price{};
    Qty        qty{};
};

// Normalized execution report (mapped from FIX ExecutionReport).
struct ExecutionReport {
    OrderId    orderId{};
    OrderState newState{OrderState::Acked};
    Qty        lastQty{};
    Price      lastPx{};
    Qty        cumQty{};
    Nanos      ts{0};
    // Self-describing like a real FIX ExecutionReport (Symbol 55 / Side 54), so
    // consumers can attribute a fill without re-looking-up the originating order.
    SymbolId   symbol{};
    Side       side{Side::Buy};
};

}  // namespace rts::oms
