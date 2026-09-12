// Core value types for the execution engine.
//
// Design: trivially-copyable, fixed-size POD-ish structs so they can live in
// pre-allocated pools / lock-free ring buffers with zero heap allocation on the
// order hot-path (ARCHITECTURE §2.4, TECH-NOTES §3.6).
#pragma once

#include <array>
#include <cstdint>
#include <string_view>

namespace exec {

enum class Side : std::uint8_t { Buy = 1, Sell = 2 };

enum class OrdType : std::uint8_t { Market = 1, Limit = 2, Stop = 3, StopLimit = 4 };

enum class ExecStatus : std::uint8_t {
    New = 0,
    Working = 1,
    PartiallyFilled = 2,
    Filled = 3,
    Cancelled = 4,
    Rejected = 5,
};

// Money as scaled integer (price * 1e4) — never floating point for cash
// (TECH-NOTES §3.6). 1 unit = 0.0001 of currency.
using Price4 = std::int64_t;
constexpr std::int64_t kPriceScale = 10000;

inline constexpr Price4 to_price4(double human) noexcept {
    return static_cast<Price4>(human * kPriceScale + (human >= 0 ? 0.5 : -0.5));
}

// Fixed 16-byte symbol avoids std::string allocation on the hot path.
using Symbol = std::array<char, 16>;

inline Symbol make_symbol(std::string_view s) noexcept {
    Symbol out{};
    const std::size_t n = s.size() < out.size() ? s.size() : out.size();
    for (std::size_t i = 0; i < n; ++i) out[i] = s[i];
    return out;
}

struct Order {
    std::uint64_t order_id{};        // internal monotonic id
    std::uint64_t client_order_id{}; // idempotency key (dedupe on reconnect)
    Symbol symbol{};
    Side side{Side::Buy};
    OrdType type{OrdType::Market};
    std::int64_t quantity{};
    Price4 limit_price{};
};

struct ExecReport {
    std::uint64_t order_id{};
    Symbol symbol{};
    ExecStatus status{ExecStatus::New};
    std::int64_t last_qty{};
    Price4 last_price{};
};

}  // namespace exec
