// ============================================================================
//  common/Types.hpp
//  Core value types shared across the trading engine.
//
//  Design notes:
//   - Prices/quantities are fixed-point integers, never floating point. Money
//     math must be exact and branch-predictable; doubles are banned on the
//     hot path.
//   - Strong typedefs prevent mixing up ids/prices/quantities at compile time.
//   - Everything here is trivially copyable POD so it can live in ring buffers
//     and memory pools with no construction cost.
// ============================================================================
#pragma once

#include <cstdint>
#include <compare>
#include <array>
#include <string_view>

namespace rts {

// ---- Fixed-point price -----------------------------------------------------
// Stored as an integer number of ticks (1e-9 of a currency unit by convention).
// Adjust kPriceScale per asset class if needed.
inline constexpr std::int64_t kPriceScale = 1'000'000'000;  // 9 dp

struct Price {
    std::int64_t ticks{0};

    constexpr auto operator<=>(const Price&) const = default;

    [[nodiscard]] constexpr double toDouble() const noexcept {
        return static_cast<double>(ticks) / static_cast<double>(kPriceScale);
    }
    [[nodiscard]] static constexpr Price fromDouble(double v) noexcept {
        return Price{static_cast<std::int64_t>(v * kPriceScale)};
    }
};

// ---- Quantity --------------------------------------------------------------
struct Qty {
    std::int64_t lots{0};
    constexpr auto operator<=>(const Qty&) const = default;
};

// ---- Strong ids ------------------------------------------------------------
enum class OrderId    : std::uint64_t {};   // engine-assigned client order id
enum class SymbolId   : std::uint32_t {};   // interned instrument id
enum class StrategyId : std::uint16_t {};
enum class VenueId    : std::uint16_t {};

// ---- Enumerations ----------------------------------------------------------
enum class Side : std::uint8_t { Buy = 1, Sell = 2 };

enum class OrdType : std::uint8_t { Market = 1, Limit = 2, Stop = 3, StopLimit = 4 };

enum class TimeInForce : std::uint8_t { Day = 1, IOC = 3, FOK = 4, GTC = 6 };

enum class OrderState : std::uint8_t {
    New = 0, PendingNew, Acked, PartiallyFilled, Filled,
    PendingCancel, Cancelled, Rejected
};

// ---- Compact, fixed-size symbol ticker (no heap) ---------------------------
// e.g. "AAPL", "BTC-USD". Up to 15 chars + NUL; comparisons are word-sized.
struct Ticker {
    std::array<char, 16> data{};

    constexpr Ticker() = default;
    explicit Ticker(std::string_view s) noexcept {
        const std::size_t n = s.size() < 15 ? s.size() : 15;
        for (std::size_t i = 0; i < n; ++i) data[i] = s[i];
    }
    [[nodiscard]] std::string_view view() const noexcept {
        std::size_t n = 0;
        while (n < data.size() && data[n] != '\0') ++n;
        return {data.data(), n};
    }
    bool operator==(const Ticker&) const = default;
};

// Nanoseconds since steady-clock epoch (see core/time/Clock.hpp).
using Nanos = std::int64_t;

}  // namespace rts
