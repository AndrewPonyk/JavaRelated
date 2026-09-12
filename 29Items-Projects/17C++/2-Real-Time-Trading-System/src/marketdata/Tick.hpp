// ============================================================================
//  marketdata/Tick.hpp
//  POD market-data events that ride the disruptor. Trivially copyable, fixed
//  size, no pointers -> safe to memcpy into ring slots.
// ============================================================================
#pragma once

#include <cstdint>

#include "common/Types.hpp"

namespace rts::md {

enum class TickType : std::uint8_t { Trade, Quote, BookUpdate };

// A single market-data event.
struct Tick {
    SymbolId symbol{};
    TickType type{TickType::Quote};
    Side     side{Side::Buy};   // for book updates
    Price    price{};
    Qty      qty{};
    Nanos    exchangeTs{0};     // venue timestamp
    Nanos    recvTs{0};         // local receive timestamp (for latency stats)
    std::uint64_t seq{0};       // venue sequence number
};

// Best bid/offer snapshot emitted to strategies after a book update.
struct BBO {
    SymbolId symbol{};
    Price    bidPx{};
    Qty      bidQty{};
    Price    askPx{};
    Qty      askQty{};
    Nanos    ts{0};

    [[nodiscard]] bool crossed() const noexcept { return bidPx >= askPx; }
    [[nodiscard]] Price mid() const noexcept {
        return Price{(bidPx.ticks + askPx.ticks) / 2};
    }
};

}  // namespace rts::md
