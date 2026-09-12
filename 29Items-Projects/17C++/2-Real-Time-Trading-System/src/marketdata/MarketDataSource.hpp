// ============================================================================
//  marketdata/MarketDataSource.hpp
//  Sources of Tick events for the engine and backtester:
//    - generateSynthetic: a deterministic random-walk L2 feed (one level per
//      side, so the book never grows and the BBO is always well-defined).
//    - readCsv / writeCsv: replay/record ticks as CSV
//      (exchangeTs,symbolId,side,price,qty).
//  These let the same engine code run live-style, in backtests, and in tests.
// ============================================================================
#pragma once

#include <cstdint>
#include <fstream>
#include <random>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "common/Types.hpp"
#include "marketdata/Tick.hpp"

namespace rts::md {

// Deterministic synthetic feed: a mean-reverting-ish random walk around a
// starting mid, emitting level replacements so exactly one bid + one ask level
// exist at a time. `steps` price moves -> up to 4 ticks each.
inline std::vector<Tick> generateSynthetic(SymbolId symbol, std::size_t steps,
                                           std::uint64_t seed = 42,
                                           double startMid = 100.0,
                                           double halfSpread = 0.05) {
    std::vector<Tick> out;
    out.reserve(steps * 4);
    std::mt19937_64 rng(seed);

    double mid = startMid;
    std::int64_t oldBid = -1, oldAsk = -1;
    std::uint64_t seq = 0;
    Nanos ts = 0;

    auto push = [&](Side side, Price px, std::int64_t qty) {
        Tick t{};
        t.symbol     = symbol;
        t.type       = TickType::BookUpdate;
        t.side       = side;
        t.price      = px;
        t.qty        = Qty{qty};
        t.exchangeTs = ts;
        t.recvTs     = ts;
        t.seq        = seq++;
        out.push_back(t);
    };

    for (std::size_t i = 0; i < steps; ++i) {
        const int move = static_cast<int>(rng() % 5) - 2;   // -2..+2 ticks
        mid += move * 0.01;
        if (mid < 1.0) mid = 1.0;

        const Price nb = Price::fromDouble(mid - halfSpread);
        const Price na = Price::fromDouble(mid + halfSpread);
        const std::int64_t bidQty = 1 + static_cast<std::int64_t>(rng() % 20);
        const std::int64_t askQty = 1 + static_cast<std::int64_t>(rng() % 20);

        if (oldBid >= 0 && oldBid != nb.ticks) push(Side::Buy, Price{oldBid}, 0);
        push(Side::Buy, nb, bidQty);
        if (oldAsk >= 0 && oldAsk != na.ticks) push(Side::Sell, Price{oldAsk}, 0);
        push(Side::Sell, na, askQty);

        oldBid = nb.ticks;
        oldAsk = na.ticks;
        ++ts;
    }
    return out;
}

inline void writeCsv(const std::string& path, const std::vector<Tick>& ticks) {
    std::ofstream out(path, std::ios::trunc);
    out << "exchangeTs,symbolId,side,price,qty\n";
    for (const auto& t : ticks) {
        out << t.exchangeTs << ','
            << static_cast<std::uint32_t>(t.symbol) << ','
            << (t.side == Side::Buy ? 'B' : 'S') << ','
            << t.price.toDouble() << ','
            << t.qty.lots << '\n';
    }
}

inline std::vector<Tick> readCsv(const std::string& path) {
    std::vector<Tick> out;
    std::ifstream in(path);
    std::string line;
    std::uint64_t seq = 0;
    bool header = true;
    while (std::getline(in, line)) {
        if (header) { header = false; continue; }   // skip column header
        if (line.empty()) continue;
        std::stringstream ss(line);
        std::string tsS, symS, sideS, pxS, qtyS;
        std::getline(ss, tsS, ',');
        std::getline(ss, symS, ',');
        std::getline(ss, sideS, ',');
        std::getline(ss, pxS, ',');
        std::getline(ss, qtyS, ',');
        if (qtyS.empty()) continue;

        // Skip malformed rows rather than aborting the whole replay.
        Tick t{};
        try {
            t.exchangeTs = std::stoll(tsS);
            t.symbol = static_cast<SymbolId>(static_cast<std::uint32_t>(std::stoul(symS)));
            t.price  = Price::fromDouble(std::stod(pxS));
            t.qty    = Qty{std::stoll(qtyS)};
        } catch (const std::exception&) {
            continue;
        }
        t.recvTs = t.exchangeTs;
        t.side   = (!sideS.empty() && sideS[0] == 'B') ? Side::Buy : Side::Sell;
        t.type   = TickType::BookUpdate;
        t.seq    = seq++;
        out.push_back(t);
    }
    return out;
}

}  // namespace rts::md
