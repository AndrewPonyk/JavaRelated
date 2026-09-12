// ============================================================================
//  marketdata/OrderBook.cpp
//  L2 book maintenance. Kept deliberately simple and branch-light; the sorted
//  insert is the hot operation. The book invariant (bids strictly below asks)
//  is asserted in debug builds.
// ============================================================================
#include "marketdata/OrderBook.hpp"

#include <algorithm>
#include <cassert>

namespace rts::md {

namespace {
// Insert/modify/remove a level in a side that is kept sorted by `less`.
template <typename Compare>
bool applyToSide(std::vector<OrderBook::Level>& side, Price px, Qty qty,
                 Compare betterThan) {
    auto it = std::find_if(side.begin(), side.end(),
                           [&](const auto& lvl) { return lvl.px == px; });
    if (qty.lots == 0) {                       // remove
        if (it == side.end()) return false;
        const bool wasTop = (it == side.begin());
        side.erase(it);
        return wasTop;
    }
    if (it != side.end()) {                    // modify in place
        it->qty = qty;
        return it == side.begin();
    }
    // insert, keeping order (best at front per `betterThan`)
    auto pos = std::find_if(side.begin(), side.end(),
                            [&](const auto& lvl) { return betterThan(px, lvl.px); });
    const bool newTop = (pos == side.begin());
    side.insert(pos, {px, qty});
    return newTop;
}
}  // namespace

bool OrderBook::apply(Side side, Price px, Qty qty) noexcept {
    bool topChanged;
    if (side == Side::Buy) {
        // bids: higher price is better
        topChanged = applyToSide(bids_, px, qty,
                                 [](Price a, Price b) { return a > b; });
    } else {
        // asks: lower price is better
        topChanged = applyToSide(asks_, px, qty,
                                 [](Price a, Price b) { return a < b; });
    }
#ifndef NDEBUG
    if (!bids_.empty() && !asks_.empty()) {
        // Sanity: best bid should be strictly below best ask in a sane book.
        assert(bids_.front().px < asks_.front().px && "crossed book");
    }
#endif
    return topChanged;
}

BBO OrderBook::topOfBook() const noexcept {
    BBO bbo{};
    bbo.symbol = symbol_;
    if (!bids_.empty()) { bbo.bidPx = bids_.front().px; bbo.bidQty = bids_.front().qty; }
    if (!asks_.empty()) { bbo.askPx = asks_.front().px; bbo.askQty = asks_.front().qty; }
    return bbo;
}

}  // namespace rts::md
