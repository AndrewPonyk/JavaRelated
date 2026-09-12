// ============================================================================
//  tests/unit/test_order_manager.cpp
//  OMS lifecycle + state machine.
// ============================================================================
#include "oms/OrderManager.hpp"

#include <gtest/gtest.h>

#include <vector>

using namespace rts;

namespace {
oms::OrderIntent intent() {
    oms::OrderIntent i{};
    i.strategy = static_cast<StrategyId>(1);
    i.symbol   = static_cast<SymbolId>(1);
    i.side     = Side::Buy;
    i.type     = OrdType::Limit;
    i.price    = Price::fromDouble(100.0);
    i.qty      = Qty{100};
    return i;
}
}  // namespace

TEST(OrderManager, CreateForwardsAndJournals) {
    std::vector<oms::Order> sent, journaled;
    oms::OrderManager oms([&](const oms::Order& o) { sent.push_back(o); },
                          [&](const oms::Order& o) { journaled.push_back(o); });
    const auto id = oms.create(intent(), 1);
    EXPECT_EQ(oms.liveCount(), 1u);
    ASSERT_EQ(sent.size(), 1u);
    EXPECT_TRUE(sent[0].state == OrderState::PendingNew);
    EXPECT_TRUE(oms.find(id) != nullptr);
}

TEST(OrderManager, PartialThenFullFillRetiresOrder) {
    oms::OrderManager oms([](const oms::Order&) {}, [](const oms::Order&) {});
    const auto id = oms.create(intent(), 1);

    oms::ExecutionReport partial{id, OrderState::PartiallyFilled, Qty{40},
                                 Price::fromDouble(100.0), Qty{40}, 2};
    oms.onExecution(partial);
    ASSERT_TRUE(oms.find(id) != nullptr);
    EXPECT_EQ(oms.find(id)->filled.lots, 40);
    EXPECT_TRUE(oms.find(id)->state == OrderState::PartiallyFilled);

    oms::ExecutionReport fill{id, OrderState::Filled, Qty{60},
                              Price::fromDouble(100.0), Qty{100}, 3};
    oms.onExecution(fill);
    EXPECT_EQ(oms.liveCount(), 0u);          // terminal -> retired
    EXPECT_TRUE(oms.find(id) == nullptr);
}

TEST(OrderManager, IllegalTransitionIgnored) {
    oms::OrderManager oms([](const oms::Order&) {}, [](const oms::Order&) {});
    const auto id = oms.create(intent(), 1);
    // Drive to Filled, then attempt a post-terminal transition.
    oms.onExecution({id, OrderState::Filled, Qty{100}, Price::fromDouble(100.0), Qty{100}, 2});
    EXPECT_EQ(oms.liveCount(), 0u);
    // A late report for a retired order must not crash or resurrect it.
    oms.onExecution({id, OrderState::PartiallyFilled, Qty{10}, Price::fromDouble(100.0), Qty{10}, 3});
    EXPECT_EQ(oms.liveCount(), 0u);
}

TEST(OrderManager, CancelMovesToPendingCancel) {
    std::vector<oms::Order> sent;
    oms::OrderManager oms([&](const oms::Order& o) { sent.push_back(o); },
                          [](const oms::Order&) {});
    const auto id = oms.create(intent(), 1);
    oms.onExecution({id, OrderState::Acked, Qty{0}, Price{}, Qty{0}, 2});
    EXPECT_TRUE(oms.cancel(id, 3));
    EXPECT_TRUE(oms.find(id)->state == OrderState::PendingCancel);
}
