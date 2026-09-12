// execution-engine entry point.
//
// Pipeline:  Kafka `orders` (risk-approved)  ->  OrderRouter  ->  FIX  ->  broker
//            broker ExecReports  ->  OrderRouter  ->  Kafka `fills`
//
// Production hardening (Phase 3): pin this thread to an isolated core (isolcpus),
// busy-poll the FIX socket, lock memory (mlockall), use huge pages.
#include <atomic>
#include <csignal>
#include <cstdio>

#include "execution/fix_session.hpp"
#include "execution/order_router.hpp"
#include "execution/types.hpp"

namespace {
std::atomic<bool> g_running{true};
void handle_signal(int) { g_running.store(false); }
}  // namespace

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;
    std::signal(SIGINT, handle_signal);
    std::signal(SIGTERM, handle_signal);

    // TODO(phase-1): read FIX_* and KAFKA_* from env (Vault-injected in prod).
    exec::QuickFixSession session("TRADINGPLATFORM", "BROKER");
    if (!session.logon()) {
        std::fprintf(stderr, "FIX logon failed — refusing to start (fail closed)\n");
        return 1;
    }

    exec::OrderRouter router(session);
    router.set_fill_sink([](const exec::ExecReport& r) {
        // TODO(phase-1): serialize and produce to Kafka `fills` (async, non-blocking).
        std::printf("fill: order_id=%llu status=%u qty=%lld\n",
                    static_cast<unsigned long long>(r.order_id),
                    static_cast<unsigned>(r.status),
                    static_cast<long long>(r.last_qty));
    });

    std::printf("execution-engine started; awaiting approved orders...\n");

    // TODO(phase-1): replace this idle loop with a Kafka consumer poll over `orders`,
    //                deserialize to exec::Order, and call router.submit(order).
    while (g_running.load()) {
        // busy/idle loop placeholder
    }

    std::printf("execution-engine shutting down (live orders: %zu)\n", router.live_order_count());
    return 0;
}
