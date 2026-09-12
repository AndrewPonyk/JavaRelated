// ============================================================================
//  risk/RiskEngine.cpp
//  Pre-trade checks. Order matters: cheapest / most-likely-to-fire first so we
//  reject fast. All integer math; the only multiply is the notional check.
// ============================================================================
#include "risk/RiskEngine.hpp"

#include <cmath>
#include <cstdlib>

namespace rts::risk {

RiskResult RiskEngine::check(const oms::OrderIntent& intent,
                             Price refPx) const noexcept {
    if (killSwitchActive()) return RiskResult::KillSwitchActive;

    // 1) Fat-finger: single-order quantity ceiling.
    if (intent.qty.lots > cfg_.maxOrderQty)
        return RiskResult::MaxOrderQtyExceeded;

    // 2) Resulting net position within bounds.
    const std::int64_t signed_qty =
        (intent.side == Side::Buy) ? intent.qty.lots : -intent.qty.lots;
    const std::int64_t projected = position(intent.symbol) + signed_qty;
    if (std::llabs(projected) > cfg_.maxPositionQty)
        return RiskResult::MaxPositionExceeded;

    // 3) Notional ceiling (price * qty), using double only for the limit compare.
    const double notional = intent.price.toDouble() *
                            static_cast<double>(intent.qty.lots);
    if (notional > cfg_.maxNotional)
        return RiskResult::MaxNotionalExceeded;

    // 4) Price collar: reject limit prices too far through the reference.
    if (refPx.ticks > 0 && intent.type == OrdType::Limit) {
        const double ref   = refPx.toDouble();
        const double px    = intent.price.toDouble();
        const double drift = std::fabs(px - ref) / ref;
        if (drift > cfg_.priceCollarPct)
            return RiskResult::PriceCollarBreach;
    }

    return RiskResult::Approved;
}

void RiskEngine::onFill(SymbolId sym, Side side, Qty qty) noexcept {
    const std::int64_t delta =
        (side == Side::Buy) ? qty.lots : -qty.lots;
    positions_[sym] += delta;
}

}  // namespace rts::risk
