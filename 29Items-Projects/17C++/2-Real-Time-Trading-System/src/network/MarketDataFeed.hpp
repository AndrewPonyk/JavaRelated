// ============================================================================
//  network/MarketDataFeed.hpp
//  Inbound market-data handler. Receives venue packets (UDP multicast for most
//  equity/futures feeds, or TCP), decodes them to Tick PODs, and publishes onto
//  the disruptor for the book builder.
//
//  Same transport seam as OrderGateway: Asio today, kernel-bypass tomorrow.
//  Sequence-gap detection lives here so the book builder can trust ordering.
// ============================================================================
#pragma once

#include <cstdint>
#include <functional>
#include <string>

#include "marketdata/Tick.hpp"

namespace rts::net {

// Publishes a decoded tick into the engine (typically Disruptor::publish).
using TickPublisher = std::function<void(const md::Tick&)>;

class MarketDataFeed {
public:
    MarketDataFeed(std::string group, std::uint16_t port)
        : mcastGroup_(std::move(group)), port_(port) {}

    void start(TickPublisher publish);   // join multicast, begin async receive
    void stop();

    [[nodiscard]] std::uint64_t gapsDetected() const noexcept { return gaps_; }

private:
    void onDatagram(const char* data, std::size_t len);   // decode -> publish
    void onGap(std::uint64_t expected, std::uint64_t got); // request recovery feed

    std::string   mcastGroup_;
    std::uint16_t port_;
    TickPublisher publish_;
    std::uint64_t expectedSeq_{0};
    std::uint64_t gaps_{0};
    // Built under RTS_ENABLE_BOOST: boost::asio::ip::udp::socket joined to multicast group; A/B line
    //       arbitration; snapshot+incremental recovery; recvTs timestamping.
};

}  // namespace rts::net
