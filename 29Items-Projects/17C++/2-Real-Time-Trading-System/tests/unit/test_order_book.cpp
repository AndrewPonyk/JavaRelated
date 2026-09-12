// ============================================================================
//  tests/unit/test_order_book.cpp
//  Unit tests for L2 book maintenance and top-of-book computation.
// ============================================================================
#include "marketdata/OrderBook.hpp"

#include <gtest/gtest.h>

using namespace rts;
using rts::md::OrderBook;

namespace {
Price px(double v) { return Price::fromDouble(v); }
Qty   qty(std::int64_t v) { return Qty{v}; }
}  // namespace

TEST(OrderBook, BuildsTopOfBook) {
    OrderBook book(static_cast<SymbolId>(1));
    book.apply(Side::Buy,  px(99.0), qty(10));
    book.apply(Side::Buy,  px(99.5), qty(5));   // better bid
    book.apply(Side::Sell, px(100.5), qty(8));
    book.apply(Side::Sell, px(100.0), qty(3));  // better ask

    const auto bbo = book.topOfBook();
    EXPECT_EQ(bbo.bidPx, px(99.5));
    EXPECT_EQ(bbo.bidQty, qty(5));
    EXPECT_EQ(bbo.askPx, px(100.0));
    EXPECT_EQ(bbo.askQty, qty(3));
    EXPECT_FALSE(bbo.crossed());
}

TEST(OrderBook, TopChangeSignalled) {
    OrderBook book(static_cast<SymbolId>(1));
    EXPECT_TRUE(book.apply(Side::Buy, px(99.0), qty(10)));   // first bid = new top
    EXPECT_FALSE(book.apply(Side::Buy, px(98.0), qty(10)));  // worse, top unchanged
    EXPECT_TRUE(book.apply(Side::Buy, px(99.5), qty(10)));   // better, top changes
}

TEST(OrderBook, ZeroQtyRemovesLevel) {
    OrderBook book(static_cast<SymbolId>(1));
    book.apply(Side::Sell, px(100.0), qty(5));
    book.apply(Side::Sell, px(101.0), qty(5));
    EXPECT_EQ(book.askLevels(), 2u);

    EXPECT_TRUE(book.apply(Side::Sell, px(100.0), qty(0)));  // remove best ask
    EXPECT_EQ(book.askLevels(), 1u);
    EXPECT_EQ(book.topOfBook().askPx, px(101.0));
}

TEST(OrderBook, ModifyExistingLevel) {
    OrderBook book(static_cast<SymbolId>(1));
    book.apply(Side::Buy, px(99.0), qty(10));
    book.apply(Side::Buy, px(99.0), qty(25));  // modify same level
    EXPECT_EQ(book.bidLevels(), 1u);
    EXPECT_EQ(book.topOfBook().bidQty, qty(25));
}
