// GoogleTest unit tests for the OrderRouter (deterministic, no network).
#include <gtest/gtest.h>

#include <vector>

#include "execution/order_router.hpp"
#include "execution/types.hpp"

namespace {

using namespace exec;

// A controllable fake FIX session for testing router logic in isolation.
class FakeSession final : public IFixSession {
public:
    bool connected{true};
    bool send_ok{true};
    std::vector<Order> sent;
    ExecReportHandler handler;

    bool logon() override { return true; }
    bool send_new_order(const Order& o) override {
        if (!send_ok) return false;
        sent.push_back(o);
        return true;
    }
    bool send_cancel(std::uint64_t) override { return true; }
    void on_exec_report(ExecReportHandler h) override { handler = std::move(h); }
    bool is_connected() const override { return connected; }
};

Order make_order(std::uint64_t client_id) {
    Order o{};
    o.client_order_id = client_id;
    o.symbol = make_symbol("AAPL");
    o.side = Side::Buy;
    o.type = OrdType::Limit;
    o.quantity = 100;
    o.limit_price = to_price4(150.25);
    return o;
}

TEST(OrderRouter, SubmitAssignsMonotonicIds) {
    FakeSession s;
    OrderRouter router(s);
    auto id1 = router.submit(make_order(1));
    auto id2 = router.submit(make_order(2));
    EXPECT_EQ(id1, 1u);
    EXPECT_EQ(id2, 2u);
    EXPECT_EQ(s.sent.size(), 2u);
}

TEST(OrderRouter, DuplicateClientOrderIdIsDeduped) {
    FakeSession s;
    OrderRouter router(s);
    EXPECT_NE(router.submit(make_order(42)), 0u);
    EXPECT_EQ(router.submit(make_order(42)), 0u);  // duplicate => rejected
    EXPECT_EQ(s.sent.size(), 1u);
}

TEST(OrderRouter, FailsClosedWhenDisconnected) {
    FakeSession s;
    s.connected = false;
    OrderRouter router(s);
    EXPECT_EQ(router.submit(make_order(1)), 0u);
    EXPECT_TRUE(s.sent.empty());
}

TEST(OrderRouter, RetryAfterSendFailureSucceeds) {
    FakeSession s;
    s.send_ok = false;
    OrderRouter router(s);
    EXPECT_EQ(router.submit(make_order(7)), 0u);  // send failed, marker rolled back
    s.send_ok = true;
    EXPECT_NE(router.submit(make_order(7)), 0u);  // retry now succeeds
}

TEST(OrderRouter, TerminalReportRetiresOrderAndForwardsFill) {
    FakeSession s;
    OrderRouter router(s);
    std::vector<ExecReport> fills;
    router.set_fill_sink([&](const ExecReport& r) { fills.push_back(r); });

    auto id = router.submit(make_order(1));
    EXPECT_EQ(router.live_order_count(), 1u);

    ExecReport rep{};
    rep.order_id = id;
    rep.status = ExecStatus::Filled;
    rep.last_qty = 100;
    s.handler(rep);  // simulate broker fill

    EXPECT_EQ(router.live_order_count(), 0u);
    ASSERT_EQ(fills.size(), 1u);
    EXPECT_EQ(fills[0].status, ExecStatus::Filled);
}

TEST(Price4, ScalesAndRoundsCorrectly) {
    EXPECT_EQ(to_price4(150.25), 1502500);
    EXPECT_EQ(to_price4(0.0001), 1);
}

}  // namespace
