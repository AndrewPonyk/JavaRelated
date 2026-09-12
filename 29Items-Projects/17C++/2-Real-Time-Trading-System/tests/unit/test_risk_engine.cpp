// ============================================================================
//  tests/unit/test_risk_engine.cpp
//  Pre-trade risk gate: each limit and the kill switch.
// ============================================================================
#include "risk/RiskEngine.hpp"

#include <gtest/gtest.h>

using namespace rts;

namespace {
config::RiskConfig limits() {
    config::RiskConfig rc{};
    rc.maxOrderQty    = 1000;
    rc.maxPositionQty = 5000;
    rc.maxNotional    = 1'000'000.0;
    rc.priceCollarPct = 0.05;
    return rc;
}
oms::OrderIntent buy(std::int64_t qty, double px) {
    oms::OrderIntent i{};
    i.symbol = static_cast<SymbolId>(1);
    i.side   = Side::Buy;
    i.type   = OrdType::Limit;
    i.price  = Price::fromDouble(px);
    i.qty    = Qty{qty};
    return i;
}
}  // namespace

TEST(RiskEngine, ApprovesWithinLimits) {
    risk::RiskEngine eng(limits());
    EXPECT_TRUE(eng.check(buy(100, 100.0), Price::fromDouble(100.0)) ==
                risk::RiskResult::Approved);
}

TEST(RiskEngine, RejectsOversizedOrder) {
    risk::RiskEngine eng(limits());
    EXPECT_TRUE(eng.check(buy(5000, 100.0), Price::fromDouble(100.0)) ==
                risk::RiskResult::MaxOrderQtyExceeded);
}

TEST(RiskEngine, RejectsPositionBreach) {
    risk::RiskEngine eng(limits());
    eng.onFill(static_cast<SymbolId>(1), Side::Buy, Qty{4900});  // near the cap
    EXPECT_TRUE(eng.check(buy(500, 100.0), Price::fromDouble(100.0)) ==
                risk::RiskResult::MaxPositionExceeded);
}

TEST(RiskEngine, RejectsNotionalBreach) {
    risk::RiskEngine eng(limits());
    // 1000 * 2000 = 2,000,000 > 1,000,000 notional cap (qty within order cap).
    EXPECT_TRUE(eng.check(buy(1000, 2000.0), Price::fromDouble(2000.0)) ==
                risk::RiskResult::MaxNotionalExceeded);
}

TEST(RiskEngine, RejectsPriceCollarBreach) {
    risk::RiskEngine eng(limits());
    // Limit 10% above a 100.0 reference, collar is 5%.
    EXPECT_TRUE(eng.check(buy(10, 110.0), Price::fromDouble(100.0)) ==
                risk::RiskResult::PriceCollarBreach);
}

TEST(RiskEngine, KillSwitchBlocksEverything) {
    risk::RiskEngine eng(limits());
    eng.setKillSwitch(true);
    EXPECT_TRUE(eng.check(buy(1, 100.0), Price::fromDouble(100.0)) ==
                risk::RiskResult::KillSwitchActive);
    eng.setKillSwitch(false);
    EXPECT_TRUE(eng.check(buy(1, 100.0), Price::fromDouble(100.0)) ==
                risk::RiskResult::Approved);
}

TEST(RiskEngine, PositionTracksSignedFills) {
    risk::RiskEngine eng(limits());
    eng.onFill(static_cast<SymbolId>(1), Side::Buy, Qty{300});
    eng.onFill(static_cast<SymbolId>(1), Side::Sell, Qty{100});
    EXPECT_EQ(eng.position(static_cast<SymbolId>(1)), 200);
}
