// Order router: the heart of the data plane.
//
// Receives risk-APPROVED orders (from Kafka, deserialized upstream), enforces
// idempotency (dedupe by client_order_id on reconnect), and dispatches to the
// FIX session. Execution reports are folded back and emitted as fills.
//
// Hot-path rules: no heap allocation, no locking, no blocking I/O here. State
// lives in pre-sized containers; ids are monotonic.
#pragma once

#include <cstdint>
#include <unordered_map>
#include <unordered_set>

#include "execution/fix_session.hpp"
#include "execution/types.hpp"

namespace exec {

// Sink for fills/exec-reports headed back to Kafka `fills`.
using FillSink = std::function<void(const ExecReport&)>;

class OrderRouter {
public:
    explicit OrderRouter(IFixSession& session, std::size_t expected_orders = 1u << 16);

    // Route a risk-approved order. Returns the assigned internal order_id, or 0
    // if rejected (duplicate client_order_id, or session disconnected => fail closed).
    std::uint64_t submit(Order order);

    // Request cancellation of a live order by internal id.
    bool cancel(std::uint64_t order_id);

    // Wire the sink that forwards execution reports onward (to Kafka).
    void set_fill_sink(FillSink sink) { fill_sink_ = std::move(sink); }

    // Called by the FIX session when an exec report arrives.
    void handle_exec_report(const ExecReport& report);

    std::size_t live_order_count() const { return live_orders_.size(); }

private:
    IFixSession& session_;
    std::uint64_t next_order_id_{1};
    std::unordered_set<std::uint64_t> seen_client_ids_;  // idempotency
    std::unordered_map<std::uint64_t, Order> live_orders_;
    FillSink fill_sink_{};
};

}  // namespace exec
