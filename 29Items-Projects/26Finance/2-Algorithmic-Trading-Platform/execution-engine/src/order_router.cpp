#include "execution/order_router.hpp"

namespace exec {

OrderRouter::OrderRouter(IFixSession& session, std::size_t expected_orders)
    : session_(session) {
    // Pre-size hash maps to avoid rehash/allocation under load (hot-path discipline).
    seen_client_ids_.reserve(expected_orders);
    live_orders_.reserve(expected_orders);

    // Fold inbound execution reports back through the router.
    session_.on_exec_report([this](const ExecReport& r) { handle_exec_report(r); });
}

std::uint64_t OrderRouter::submit(Order order) {
    // Fail closed: never send while disconnected.
    if (!session_.is_connected()) {
        return 0;
    }

    // Idempotency: a duplicate client_order_id (e.g. Kafka at-least-once redelivery
    // after a crash) must not produce a second live order (TECH-NOTES §3.6).
    if (!seen_client_ids_.insert(order.client_order_id).second) {
        return 0;
    }

    order.order_id = next_order_id_++;
    if (!session_.send_new_order(order)) {
        // Roll back idempotency marker so a retry can succeed once reconnected.
        seen_client_ids_.erase(order.client_order_id);
        return 0;
    }

    live_orders_.emplace(order.order_id, order);
    return order.order_id;
}

bool OrderRouter::cancel(std::uint64_t order_id) {
    auto it = live_orders_.find(order_id);
    if (it == live_orders_.end()) {
        return false;
    }
    return session_.send_cancel(order_id);
}

void OrderRouter::handle_exec_report(const ExecReport& report) {
    // Terminal states retire the order from the live book.
    if (report.status == ExecStatus::Filled || report.status == ExecStatus::Cancelled ||
        report.status == ExecStatus::Rejected) {
        live_orders_.erase(report.order_id);
    }

    // Forward to Kafka `fills` via the injected sink (non-blocking async producer).
    if (fill_sink_) {
        fill_sink_(report);
    }
}

}  // namespace exec
