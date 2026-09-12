// ============================================================================
//  marketdata/OrderBook.hpp
//  L2 limit order book for a single instrument. Aggregated price levels per
//  side, sorted for O(1) best-of-book access. Designed for cache-friendly
//  updates on the hot path.
//
//  This demo uses sorted vectors of price levels (excellent locality for the
//  shallow books typical in equities/futures). For very deep books a flat
//  array indexed by price offset ("price ladder") is the production choice.
// ============================================================================
#pragma once

#include <cstddef>
#include <vector>

#include "common/Types.hpp"
#include "marketdata/Tick.hpp"

namespace rts::md {

class OrderBook {
public:
    struct Level { Price px; Qty qty; };

    explicit OrderBook(SymbolId sym) : symbol_(sym) {
        bids_.reserve(256);
        asks_.reserve(256);
    }

    // Apply a single L2 update (add/modify/remove a price level).
    // qty == 0 removes the level. Returns true if the top-of-book changed.
    bool apply(Side side, Price px, Qty qty) noexcept;

    [[nodiscard]] BBO topOfBook() const noexcept;

    [[nodiscard]] bool empty() const noexcept {
        return bids_.empty() || asks_.empty();
    }
    [[nodiscard]] SymbolId symbol() const noexcept { return symbol_; }

    // Depth accessors for analytics / dashboard.
    [[nodiscard]] std::size_t bidLevels() const noexcept { return bids_.size(); }
    [[nodiscard]] std::size_t askLevels() const noexcept { return asks_.size(); }

private:
    // bids_ sorted descending (best = front), asks_ ascending (best = front).
    SymbolId           symbol_;
    std::vector<Level> bids_;
    std::vector<Level> asks_;
};

}  // namespace rts::md
