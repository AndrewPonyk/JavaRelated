// ============================================================================
//  fix/FixCodec.hpp
//  Domain <-> FIX mapping. Encodes orders/executions to framed FIX messages and
//  decodes inbound messages back into engine PODs. Built on FixBuilder/FixParser.
//
//  ClOrdID(11) carries the engine's numeric OrderId as a decimal string, which
//  is how the engine correlates execution reports back to live orders.
// ============================================================================
#pragma once

#include <charconv>
#include <optional>
#include <string>

#include "fix/FixMessage.hpp"
#include "fix/FixParser.hpp"
#include "oms/Order.hpp"

namespace rts::fix {

// ---- enum <-> FIX scalar helpers ------------------------------------------
[[nodiscard]] inline char sideToFix(Side s) noexcept {
    return s == Side::Buy ? '1' : '2';
}
[[nodiscard]] inline Side fixToSide(std::string_view v) noexcept {
    return (!v.empty() && v[0] == '1') ? Side::Buy : Side::Sell;
}
[[nodiscard]] inline char ordTypeToFix(OrdType t) noexcept {
    switch (t) {
        case OrdType::Market:    return '1';
        case OrdType::Limit:     return '2';
        case OrdType::Stop:      return '3';
        case OrdType::StopLimit: return '4';
    }
    return '2';
}
[[nodiscard]] inline OrdType fixToOrdType(std::string_view v) noexcept {
    if (v == "1") return OrdType::Market;
    if (v == "3") return OrdType::Stop;
    if (v == "4") return OrdType::StopLimit;
    return OrdType::Limit;
}
[[nodiscard]] inline char tifToFix(TimeInForce t) noexcept {
    switch (t) {
        case TimeInForce::Day: return '0';
        case TimeInForce::IOC: return '3';
        case TimeInForce::FOK: return '4';
        case TimeInForce::GTC: return '1';
    }
    return '0';
}
[[nodiscard]] inline TimeInForce fixToTif(std::string_view v) noexcept {
    if (v == "3") return TimeInForce::IOC;
    if (v == "4") return TimeInForce::FOK;
    if (v == "1") return TimeInForce::GTC;
    return TimeInForce::Day;
}

// OrdStatus(39) drives the engine-side order state.
[[nodiscard]] inline char stateToOrdStatus(OrderState s) noexcept {
    switch (s) {
        case OrderState::Acked:           return '0';
        case OrderState::PartiallyFilled: return '1';
        case OrderState::Filled:          return '2';
        case OrderState::Cancelled:       return '4';
        case OrderState::PendingCancel:   return '6';
        case OrderState::Rejected:        return '8';
        case OrderState::PendingNew:      return 'A';
        default:                          return '0';
    }
}
[[nodiscard]] inline OrderState ordStatusToState(std::string_view v) noexcept {
    if (v.empty()) return OrderState::Acked;
    switch (v[0]) {
        case '1': return OrderState::PartiallyFilled;
        case '2': return OrderState::Filled;
        case '4': return OrderState::Cancelled;
        case '6': return OrderState::PendingCancel;
        case '8': return OrderState::Rejected;
        case 'A': return OrderState::PendingNew;
        default:  return OrderState::Acked;
    }
}

struct SessionIds {
    std::string_view sender;
    std::string_view target;
    std::string_view sendingTime{"20250101-00:00:00.000"};  // sim default
};

class FixCodec {
public:
    // ----- Engine -> exchange: NewOrderSingle (35=D) -----------------------
    [[nodiscard]] static std::string encodeNewOrderSingle(
        const oms::Order& o, std::int64_t seqNum, const SessionIds& ids) {
        // Side / OrdType / TIF are single-char FIX values.
        const char sd = sideToFix(o.side);
        const char ot = ordTypeToFix(o.type);
        const char tf = tifToFix(o.tif);

        FixBuilder b;
        b.field(tag::MsgType, msgtype::NewOrderSingle)
            .field(tag::SenderCompID, ids.sender)
            .field(tag::TargetCompID, ids.target)
            .field(tag::MsgSeqNum, seqNum)
            .field(tag::SendingTime, ids.sendingTime)
            .field(tag::ClOrdID, std::to_string(static_cast<std::uint64_t>(o.id)))
            .field(tag::Symbol, std::to_string(static_cast<std::uint32_t>(o.symbol)))
            .field(tag::Side, std::string_view(&sd, 1))
            .field(tag::OrderQty, o.qty.lots)
            .field(tag::OrdType, std::string_view(&ot, 1));
        if (o.type != OrdType::Market) b.priceField(tag::Price, o.price);
        b.field(tag::TimeInForce, std::string_view(&tf, 1));
        return b.finish();
    }

    // ----- Exchange -> engine: ExecutionReport (35=8) ----------------------
    [[nodiscard]] static std::string encodeExecutionReport(
        const oms::Order& o, const oms::ExecutionReport& er, std::int64_t seqNum,
        std::int64_t execId, const SessionIds& ids) {
        FixBuilder b;
        const char sd  = sideToFix(o.side);
        const char st  = stateToOrdStatus(er.newState);
        b.field(tag::MsgType, msgtype::ExecutionReport)
            .field(tag::SenderCompID, ids.sender)
            .field(tag::TargetCompID, ids.target)
            .field(tag::MsgSeqNum, seqNum)
            .field(tag::SendingTime, ids.sendingTime)
            .field(tag::OrderID, std::to_string(static_cast<std::uint64_t>(er.orderId)))
            .field(tag::ExecID, execId)
            .field(tag::ClOrdID, std::to_string(static_cast<std::uint64_t>(er.orderId)))
            .field(tag::ExecType, std::string_view(&st, 1))
            .field(tag::OrdStatus, std::string_view(&st, 1))
            .field(tag::Symbol, std::to_string(static_cast<std::uint32_t>(o.symbol)))
            .field(tag::Side, std::string_view(&sd, 1))
            .field(tag::LastQty, er.lastQty.lots)
            .priceField(tag::LastPx, er.lastPx)
            .field(tag::CumQty, er.cumQty.lots)
            .field(tag::LeavesQty, o.qty.lots - er.cumQty.lots);
        return b.finish();
    }

    // ----- Decoders --------------------------------------------------------
    [[nodiscard]] static oms::Order decodeNewOrderSingle(const FixMessageView& m) {
        oms::Order o{};
        o.id     = static_cast<OrderId>(toU64(m.get(tag::ClOrdID)));
        o.symbol = static_cast<SymbolId>(
            static_cast<std::uint32_t>(toU64(m.get(tag::Symbol))));
        o.side   = fixToSide(m.get(tag::Side).value_or("1"));
        o.type   = fixToOrdType(m.get(tag::OrdType).value_or("2"));
        o.tif    = fixToTif(m.get(tag::TimeInForce).value_or("0"));
        o.qty    = Qty{static_cast<std::int64_t>(m.getInt(tag::OrderQty).value_or(0))};
        if (auto px = m.get(tag::Price)) o.price = parsePrice(*px);
        o.state  = OrderState::New;
        return o;
    }

    [[nodiscard]] static oms::ExecutionReport decodeExecutionReport(
        const FixMessageView& m) {
        oms::ExecutionReport er{};
        er.orderId  = static_cast<OrderId>(toU64(m.get(tag::ClOrdID)));
        er.newState = ordStatusToState(m.get(tag::OrdStatus).value_or("0"));
        er.lastQty  = Qty{static_cast<std::int64_t>(m.getInt(tag::LastQty).value_or(0))};
        er.cumQty   = Qty{static_cast<std::int64_t>(m.getInt(tag::CumQty).value_or(0))};
        if (auto px = m.get(tag::LastPx)) er.lastPx = parsePrice(*px);
        er.symbol = static_cast<SymbolId>(
            static_cast<std::uint32_t>(toU64(m.get(tag::Symbol))));
        if (auto sd = m.get(tag::Side)) er.side = fixToSide(*sd);
        return er;
    }

private:
    static std::uint64_t toU64(std::optional<std::string_view> v) noexcept {
        if (!v) return 0;
        std::uint64_t out = 0;
        std::from_chars(v->data(), v->data() + v->size(), out);
        return out;
    }
};

}  // namespace rts::fix
