// ============================================================================
//  tests/unit/test_fix_codec.cpp
//  FIX framing/checksum + domain encode/decode round-trips.
// ============================================================================
#include "fix/FixCodec.hpp"
#include "fix/FixParser.hpp"

#include <gtest/gtest.h>

using namespace rts;
using namespace rts::fix;

namespace {
oms::Order makeOrder() {
    oms::Order o{};
    o.id    = static_cast<OrderId>(42);
    o.symbol = static_cast<SymbolId>(7);
    o.side  = Side::Sell;
    o.type  = OrdType::Limit;
    o.tif   = TimeInForce::IOC;
    o.price = Price::fromDouble(123.45);
    o.qty   = Qty{500};
    return o;
}
const SessionIds kIds{"SENDER", "TARGET", "20250101-00:00:00.000"};
}  // namespace

TEST(FixCodec, ChecksumAndFramingValidate) {
    const std::string msg = FixCodec::encodeNewOrderSingle(makeOrder(), 2, kIds);
    EXPECT_TRUE(FixParser::validate(msg));
    // Message must begin with BeginString and end with a 3-digit checksum + SOH.
    EXPECT_EQ(msg.compare(0, 2, "8="), 0);
    EXPECT_EQ(msg[msg.size() - 1], SOH);
}

TEST(FixCodec, CorruptedMessageFailsValidation) {
    std::string msg = FixCodec::encodeNewOrderSingle(makeOrder(), 2, kIds);
    msg[msg.size() / 2] ^= 0x20;  // flip a byte in the body
    EXPECT_FALSE(FixParser::validate(msg));
}

TEST(FixCodec, NewOrderSingleRoundTrip) {
    const oms::Order o = makeOrder();
    const std::string msg = FixCodec::encodeNewOrderSingle(o, 2, kIds);

    FixMessageView v;
    std::size_t consumed = 0;
    ASSERT_TRUE(FixParser::parse(msg, v, consumed));
    EXPECT_EQ(consumed, msg.size());
    EXPECT_EQ(v.msgType(), msgtype::NewOrderSingle);

    const oms::Order d = FixCodec::decodeNewOrderSingle(v);
    EXPECT_EQ(static_cast<std::uint64_t>(d.id), 42u);
    EXPECT_EQ(static_cast<std::uint32_t>(d.symbol), 7u);
    EXPECT_TRUE(d.side == Side::Sell);
    EXPECT_TRUE(d.type == OrdType::Limit);
    EXPECT_TRUE(d.tif == TimeInForce::IOC);
    EXPECT_EQ(d.qty.lots, 500);
    EXPECT_EQ(d.price, Price::fromDouble(123.45));
}

TEST(FixCodec, ExecutionReportRoundTrip) {
    const oms::Order o = makeOrder();
    oms::ExecutionReport er{};
    er.orderId  = static_cast<OrderId>(42);
    er.newState = OrderState::PartiallyFilled;
    er.lastQty  = Qty{11};
    er.lastPx   = Price::fromDouble(99.97);
    er.cumQty   = Qty{11};

    const std::string msg = FixCodec::encodeExecutionReport(o, er, 3, 1, kIds);
    EXPECT_TRUE(FixParser::validate(msg));

    FixMessageView v;
    std::size_t consumed = 0;
    ASSERT_TRUE(FixParser::parse(msg, v, consumed));
    EXPECT_EQ(v.msgType(), msgtype::ExecutionReport);

    const oms::ExecutionReport d = FixCodec::decodeExecutionReport(v);
    EXPECT_EQ(static_cast<std::uint64_t>(d.orderId), 42u);
    EXPECT_TRUE(d.newState == OrderState::PartiallyFilled);
    EXPECT_EQ(d.lastQty.lots, 11);
    EXPECT_EQ(d.cumQty.lots, 11);
    EXPECT_EQ(d.lastPx, Price::fromDouble(99.97));
    // Self-describing: Symbol(55) and Side(54) survive the round-trip.
    EXPECT_EQ(static_cast<std::uint32_t>(d.symbol), 7u);
    EXPECT_TRUE(d.side == Side::Sell);
}

TEST(FixCodec, ParseDetectsIncompleteBuffer) {
    std::string msg = FixCodec::encodeNewOrderSingle(makeOrder(), 2, kIds);
    const std::string partial = msg.substr(0, msg.size() - 5);  // truncated
    FixMessageView v;
    std::size_t consumed = 0;
    EXPECT_FALSE(FixParser::parse(partial, v, consumed));  // need more bytes
}
