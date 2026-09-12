// ============================================================================
//  tests/unit/test_robustness.cpp
//  Error-scenario / edge-case coverage: malformed external input must never
//  crash; boundaries behave; self-describing exec reports drive inventory.
// ============================================================================
#include "common/Config.hpp"
#include "fix/FixCodec.hpp"
#include "fix/FixParser.hpp"
#include "marketdata/MarketDataSource.hpp"
#include "marketdata/OrderBook.hpp"
#include "oms/OrderManager.hpp"
#include "risk/RiskEngine.hpp"
#include "strategy/strategies/MeanReversionStrategy.hpp"

#include <gtest/gtest.h>

#include <cstdio>
#include <fstream>

using namespace rts;

// ---- Config: malformed numbers are skipped, not fatal ----------------------
TEST(Robustness, ConfigSkipsMalformedNumbersWithoutCrashing) {
    const std::string path = "robust_cfg_tmp.yaml";
    std::ofstream(path, std::ios::trunc)
        << "trading_cores: [2, oops, 4]\n"
        << "fix:\n  port: notaport\n"
        << "instruments:\n  - { symbol_id: NaN, ticker: AAPL, venue: XNAS }\n";
    const auto cfg = config::EngineConfig::load(path);  // must not throw
    std::remove(path.c_str());

    ASSERT_EQ(cfg.tradingCores.size(), 2u);   // "oops" skipped
    EXPECT_EQ(cfg.tradingCores[0], 2u);
    EXPECT_EQ(cfg.tradingCores[1], 4u);
    ASSERT_EQ(cfg.instruments.size(), 1u);
    EXPECT_EQ(cfg.instruments[0].symbolId, 0u);  // "NaN" -> 0
}

// ---- CSV: malformed rows are skipped, valid rows kept ----------------------
TEST(Robustness, ReadCsvSkipsMalformedLines) {
    const std::string path = "robust_ticks_tmp.csv";
    std::ofstream(path, std::ios::trunc)
        << "exchangeTs,symbolId,side,price,qty\n"
        << "1,1,B,100.0,5\n"
        << "garbage line without commas\n"   // too few fields -> skipped
        << "2,1,S,bad_price,7\n"             // unparseable price -> skipped
        << "3,1,B,101.0,9\n";
    const auto ticks = md::readCsv(path);
    std::remove(path.c_str());

    ASSERT_EQ(ticks.size(), 2u);
    EXPECT_EQ(ticks[0].qty.lots, 5);
    EXPECT_EQ(ticks[1].qty.lots, 9);
}

// ---- FIX parse: empty / garbage / incomplete all return false safely -------
TEST(Robustness, FixParseHandlesBadInput) {
    fix::FixMessageView v;
    std::size_t consumed = 0;
    EXPECT_FALSE(fix::FixParser::parse("", v, consumed));
    EXPECT_FALSE(fix::FixParser::parse("not-a-fix-message", v, consumed));
    EXPECT_FALSE(fix::FixParser::parse(std::string("8=FIX.4.4\x01"), v, consumed));
}

// ---- OrderBook: empty book + removing a nonexistent level -------------------
TEST(Robustness, OrderBookEmptyAndRemoveNonexistent) {
    md::OrderBook book(static_cast<SymbolId>(1));
    const auto bbo = book.topOfBook();
    EXPECT_EQ(bbo.bidPx.ticks, 0);
    EXPECT_EQ(bbo.askPx.ticks, 0);
    EXPECT_FALSE(book.apply(Side::Buy, Price::fromDouble(100.0), Qty{0}));
}

// ---- Risk: boundary is inclusive (check is strict >) -----------------------
TEST(Robustness, RiskBoundaryExactlyAtLimitApproved) {
    config::RiskConfig rc{};
    rc.maxOrderQty = 100; rc.maxPositionQty = 100;
    rc.maxNotional = 10000.0; rc.priceCollarPct = 0.05;
    risk::RiskEngine eng(rc);

    oms::OrderIntent i{};
    i.symbol = static_cast<SymbolId>(1); i.side = Side::Buy; i.type = OrdType::Limit;
    i.qty = Qty{100}; i.price = Price::fromDouble(100.0);   // qty==cap, notional==cap
    EXPECT_TRUE(eng.check(i, Price::fromDouble(100.0)) == risk::RiskResult::Approved);

    i.qty = Qty{101};
    EXPECT_TRUE(eng.check(i, Price::fromDouble(100.0)) ==
                risk::RiskResult::MaxOrderQtyExceeded);
}

TEST(Robustness, RiskZeroQtyIsBenign) {
    risk::RiskEngine eng(config::RiskConfig{});
    oms::OrderIntent i{};
    i.symbol = static_cast<SymbolId>(1); i.qty = Qty{0}; i.price = Price::fromDouble(100.0);
    EXPECT_TRUE(eng.check(i, Price::fromDouble(100.0)) == risk::RiskResult::Approved);
}

// ---- Self-describing execution report drives strategy inventory ------------
namespace {
struct NoopContext final : strategy::StrategyContext {
    void submit(const oms::OrderIntent&) override {}
    void cancel(OrderId) override {}
    [[nodiscard]] Nanos now() const noexcept override { return 0; }
};
oms::ExecutionReport fill(SymbolId sym, Side side, std::int64_t qty) {
    oms::ExecutionReport er{};
    er.symbol = sym; er.side = side; er.lastQty = Qty{qty};
    er.newState = OrderState::PartiallyFilled;
    return er;
}
}  // namespace

TEST(Robustness, StrategyTracksInventoryFromFills) {
    strategy::MeanReversionStrategy s(static_cast<StrategyId>(1),
                                      static_cast<SymbolId>(1), Qty{100});
    NoopContext ctx;
    s.onFill(fill(static_cast<SymbolId>(1), Side::Buy, 30), ctx);
    s.onFill(fill(static_cast<SymbolId>(1), Side::Sell, 10), ctx);
    s.onFill(fill(static_cast<SymbolId>(2), Side::Buy, 50), ctx);  // other symbol -> ignored
    EXPECT_EQ(s.inventory(), 20);
}

// ---- OMS counts stray reports instead of crashing --------------------------
TEST(Robustness, OmsCountsUnknownReports) {
    oms::OrderManager oms([](const oms::Order&) {}, [](const oms::Order&) {});
    oms::ExecutionReport er{};
    er.orderId = static_cast<OrderId>(999);
    er.newState = OrderState::Filled;
    er.lastQty = Qty{1}; er.cumQty = Qty{1};
    oms.onExecution(er);
    EXPECT_EQ(oms.unknownReports(), 1u);
    EXPECT_EQ(oms.liveCount(), 0u);
}
