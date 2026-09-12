// ============================================================================
//  exchange/SimulatedExchange.cpp
//  Matching + FIX admin for the simulated venue.
// ============================================================================
#include "exchange/SimulatedExchange.hpp"

#include <algorithm>

#include "common/Logger.hpp"
#include "fix/FixCodec.hpp"
#include "fix/FixParser.hpp"

namespace rts::exchange {

SimulatedExchange::SimulatedExchange(std::string compId, std::string targetCompId)
    : compId_(std::move(compId)), targetCompId_(std::move(targetCompId)) {}

md::OrderBook& SimulatedExchange::bookFor(SymbolId sym) {
    const auto key = static_cast<std::uint32_t>(sym);
    auto it = books_.find(key);
    if (it == books_.end()) it = books_.emplace(key, md::OrderBook(sym)).first;
    return it->second;
}

void SimulatedExchange::onTick(const md::Tick& t) {
    bookFor(t.symbol).apply(t.side, t.price, t.qty);
}

void SimulatedExchange::reply(const std::string& framed) {
    if (toEngine_) toEngine_(framed.data(), framed.size());
}

void SimulatedExchange::sendLogon() {
    fix::FixBuilder b;
    b.field(fix::tag::MsgType, fix::msgtype::Logon)
        .field(fix::tag::SenderCompID, compId_)
        .field(fix::tag::TargetCompID, targetCompId_)
        .field(fix::tag::MsgSeqNum, static_cast<std::int64_t>(outSeq_++))
        .field(fix::tag::SendingTime, fix::nowFixTimestamp())
        .field(fix::tag::EncryptMethod, std::int64_t{0})
        .field(fix::tag::HeartBtInt, std::int64_t{30});
    reply(b.finish());
}

void SimulatedExchange::handleLogon() {
    loggedOn_ = true;
    sendLogon();   // acknowledge the engine's logon
    RTS_INFO("exchange: engine logged on");
}

void SimulatedExchange::sendExec(const oms::Order& order,
                                 const oms::ExecutionReport& er) {
    const fix::SessionIds ids{compId_, targetCompId_, "20250101-00:00:00.000"};
    const std::string msg = fix::FixCodec::encodeExecutionReport(
        order, er, static_cast<std::int64_t>(outSeq_++),
        static_cast<std::int64_t>(execId_++), ids);
    ++execsSent_;
    reply(msg);
}

void SimulatedExchange::handleNewOrder(const fix::FixMessageView& msg) {
    const oms::Order order = fix::FixCodec::decodeNewOrderSingle(msg);
    ++ordersReceived_;

    const md::BBO bbo = bookFor(order.symbol).topOfBook();

    bool  marketable = false;
    Price fillPx{};
    std::int64_t avail = 0;
    if (order.side == Side::Buy) {
        if (bbo.askQty.lots > 0 &&
            (order.type == OrdType::Market || order.price >= bbo.askPx)) {
            marketable = true;
            fillPx     = bbo.askPx;
            avail      = bbo.askQty.lots;
        }
    } else {
        if (bbo.bidQty.lots > 0 &&
            (order.type == OrdType::Market || order.price <= bbo.bidPx)) {
            marketable = true;
            fillPx     = bbo.bidPx;
            avail      = bbo.bidQty.lots;
        }
    }

    if (marketable) {
        const std::int64_t fillQty = std::min(order.qty.lots, avail);
        oms::ExecutionReport er{};
        er.orderId = order.id;
        er.lastQty = Qty{fillQty};
        er.lastPx  = fillPx;
        er.cumQty  = Qty{fillQty};
        er.newState = (fillQty == order.qty.lots) ? OrderState::Filled
                                                  : OrderState::PartiallyFilled;
        sendExec(order, er);

        // IOC/FOK: cancel any unfilled remainder.
        if (er.newState == OrderState::PartiallyFilled &&
            (order.tif == TimeInForce::IOC || order.tif == TimeInForce::FOK)) {
            oms::ExecutionReport cx{};
            cx.orderId  = order.id;
            cx.newState = OrderState::Cancelled;
            cx.cumQty   = Qty{fillQty};
            sendExec(order, cx);
        }
    } else {
        // Not marketable: IOC/FOK cancel immediately; others would rest (acked).
        oms::ExecutionReport er{};
        er.orderId  = order.id;
        er.newState = (order.tif == TimeInForce::IOC || order.tif == TimeInForce::FOK)
                          ? OrderState::Cancelled
                          : OrderState::Acked;
        sendExec(order, er);
    }
}

void SimulatedExchange::onBytes(const char* data, std::size_t len) {
    constexpr std::size_t kMaxRxBuffer = 1u << 20;  // 1 MiB cap (anti-DoS)
    rxBuffer_.append(data, len);

    fix::FixMessageView msg;
    std::size_t consumed = 0;
    while (fix::FixParser::parse(rxBuffer_, msg, consumed, /*verify=*/true)) {
        const auto mt = msg.msgType();
        if (mt == fix::msgtype::Logon) {
            handleLogon();
        } else if (mt == fix::msgtype::NewOrderSingle) {
            handleNewOrder(msg);
        } else if (mt == fix::msgtype::Logout) {
            loggedOn_ = false;
        }
        // Heartbeat / TestRequest: ignored by the sim acceptor.
        rxBuffer_.erase(0, consumed);
        msg = fix::FixMessageView{};
    }
    if (rxBuffer_.size() > kMaxRxBuffer) {
        RTS_ERROR("exchange rx buffer overflow; clearing",
                  static_cast<std::int64_t>(rxBuffer_.size()));
        rxBuffer_.clear();
    }
}

}  // namespace rts::exchange
