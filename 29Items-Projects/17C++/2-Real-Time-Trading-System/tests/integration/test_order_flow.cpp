// ============================================================================
//  tests/integration/test_order_flow.cpp
//  End-to-end order flow: strategy intent -> risk -> OMS -> (captured) gateway,
//  then a simulated execution report back through the OMS. Validates that the
//  pieces compose and that state transitions are correct.
//
//  A full integration test additionally stands up a FIX acceptor (simulated
//  exchange) over the real FIX wire is covered by test_engine_sim.cpp.
// ============================================================================
#include <gtest/gtest.h>

#include <vector>

#include "oms/OrderManager.hpp"
#include "risk/RiskEngine.hpp"

using namespace rts;

TEST(OrderFlow, ApprovedIntentBecomesLiveOrderThenFills) {
    config::RiskConfig rc{};
    rc.maxOrderQty   = 1000;
    rc.maxPositionQty = 10000;
    rc.maxNotional   = 1e9;
    rc.priceCollarPct = 0.10;
    risk::RiskEngine risk(rc);

    std::vector<oms::Order> sent;
    std::vector<oms::Order> journaled;
    oms::OrderManager oms(
        [&](const oms::Order& o) { sent.push_back(o); },
        [&](const oms::Order& o) { journaled.push_back(o); });

    oms::OrderIntent intent{};
    intent.strategy = static_cast<StrategyId>(1);
    intent.symbol   = static_cast<SymbolId>(1);
    intent.side     = Side::Buy;
    intent.type     = OrdType::Limit;
    intent.price    = Price::fromDouble(100.0);
    intent.qty      = Qty{100};

    // Risk gate.
    ASSERT_EQ(risk.check(intent, Price::fromDouble(100.0)),
              risk::RiskResult::Approved);

    // OMS creates the order and forwards it.
    const auto id = oms.create(intent, /*now=*/1);
    ASSERT_EQ(oms.liveCount(), 1u);
    ASSERT_EQ(sent.size(), 1u);
    EXPECT_EQ(sent.front().state, OrderState::PendingNew);

    // Exchange acks, then fully fills.
    oms::ExecutionReport ack{id, OrderState::Acked, Qty{0}, {}, Qty{0}, 2};
    oms.onExecution(ack);
    EXPECT_EQ(oms.find(id)->state, OrderState::Acked);

    oms::ExecutionReport fill{id, OrderState::Filled, Qty{100},
                              Price::fromDouble(100.0), Qty{100}, 3};
    oms.onExecution(fill);
    EXPECT_EQ(oms.liveCount(), 0u);  // terminal -> removed from live set
}

TEST(OrderFlow, KillSwitchBlocksEverything) {
    config::RiskConfig rc{};
    rc.maxOrderQty = 1000; rc.maxPositionQty = 10000; rc.maxNotional = 1e9;
    risk::RiskEngine risk(rc);
    risk.setKillSwitch(true);

    oms::OrderIntent intent{};
    intent.qty = Qty{1};
    EXPECT_EQ(risk.check(intent, Price::fromDouble(100.0)),
              risk::RiskResult::KillSwitchActive);
}

// Note: a networked variant (QuickFIX acceptor on a loopback port driving the
//       RTS_ENABLE_BOOST OrderGateway) would add over-the-socket coverage; the
//       in-process wire round-trip is already covered by test_engine_sim.cpp.
