// ============================================================================
//  risk/RiskEngine.hpp
//  Mandatory, synchronous pre-trade risk gate. Every order intent passes
//  through check() BEFORE it can be encoded and sent. Checks are O(1), branch-
//  predictable, and allocation-free — they sit squarely on the hot path.
//
//  Includes a global kill-switch the dashboard can trip to halt all trading.
// ============================================================================
#pragma once

#include <atomic>
#include <cstdint>
#include <unordered_map>

#include "common/Config.hpp"
#include "common/Types.hpp"
#include "oms/Order.hpp"

namespace rts::risk {

enum class RiskResult : std::uint8_t {
    Approved = 0,
    KillSwitchActive,
    MaxOrderQtyExceeded,
    MaxPositionExceeded,
    MaxNotionalExceeded,
    PriceCollarBreach,
    UnknownSymbol
};

[[nodiscard]] constexpr const char* toString(RiskResult r) noexcept {
    switch (r) {
        case RiskResult::Approved:             return "Approved";
        case RiskResult::KillSwitchActive:     return "KillSwitchActive";
        case RiskResult::MaxOrderQtyExceeded:  return "MaxOrderQtyExceeded";
        case RiskResult::MaxPositionExceeded:  return "MaxPositionExceeded";
        case RiskResult::MaxNotionalExceeded:  return "MaxNotionalExceeded";
        case RiskResult::PriceCollarBreach:    return "PriceCollarBreach";
        case RiskResult::UnknownSymbol:        return "UnknownSymbol";
    }
    return "?";
}

class RiskEngine {
public:
    explicit RiskEngine(config::RiskConfig cfg) : cfg_(cfg) {}

    // The gate. `refPx` is the current reference price (e.g. mid) for the
    // price-collar / fat-finger check. Returns Approved or a specific reason.
    [[nodiscard]] RiskResult check(const oms::OrderIntent& intent,
                                   Price refPx) const noexcept;

    // Position bookkeeping, updated on fills (single-writer: the risk thread).
    void onFill(SymbolId sym, Side side, Qty qty) noexcept;

    // Operator kill-switch (set from the dashboard control channel).
    void setKillSwitch(bool on) noexcept {
        killSwitch_.store(on, std::memory_order_release);
    }
    [[nodiscard]] bool killSwitchActive() const noexcept {
        return killSwitch_.load(std::memory_order_acquire);
    }

    [[nodiscard]] std::int64_t position(SymbolId sym) const noexcept {
        auto it = positions_.find(sym);
        return it == positions_.end() ? 0 : it->second;
    }

private:
    config::RiskConfig                       cfg_;
    std::atomic<bool>                        killSwitch_{false};
    std::unordered_map<SymbolId, std::int64_t> positions_;  // signed net qty
};

}  // namespace rts::risk
