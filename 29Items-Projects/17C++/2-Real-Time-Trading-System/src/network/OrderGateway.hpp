// ============================================================================
//  network/OrderGateway.hpp
//  Outbound order session to the exchange over Boost.Asio TCP, carrying FIX.
//
//  Transport seam: this interface deliberately hides Asio so the implementation
//  can be swapped for a kernel-bypass stack (Solarflare ef_vi / DPDK) without
//  touching the OMS or strategies. One io_context pinned to one core; handlers
//  must never block.
// ============================================================================
#pragma once

#include <cstddef>
#include <functional>
#include <string>

#include "fix/FixSession.hpp"

namespace rts::net {

using BytesReceived = std::function<void(const char*, std::size_t)>;

class OrderGateway {
public:
    OrderGateway(std::string host, std::uint16_t port) : host_(std::move(host)), port_(port) {}

    void connect(BytesReceived onBytes);   // async connect + read loop
    void disconnect();

    // Push framed FIX bytes to the wire (called by FixSession's WireSink).
    void send(const char* data, std::size_t len);

    [[nodiscard]] bool connected() const noexcept { return connected_; }

private:
    void scheduleReconnect();   // exponential backoff on drop

    std::string   host_;
    std::uint16_t port_;
    bool          connected_{false};
    BytesReceived onBytes_;
    // Built under RTS_ENABLE_BOOST: boost::asio::io_context, ip::tcp::socket, strand, write queue.
    //       Pin the io_context thread; use TCP_NODELAY; busy-poll if isolated.
};

}  // namespace rts::net
