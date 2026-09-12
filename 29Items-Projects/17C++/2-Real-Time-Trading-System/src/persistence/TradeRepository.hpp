// ============================================================================
//  persistence/TradeRepository.hpp
//  Data-access object for the trading system of record. Exposes async, batched
//  journaling for the hot path plus synchronous queries for the dashboard /
//  reporting / start-of-day position load.
// ============================================================================
#pragma once

#include <memory>
#include <vector>

#include "core/lockfree/SPSCQueue.hpp"
#include "oms/Order.hpp"
#include "persistence/OracleConnection.hpp"

namespace rts::persistence {

class TradeRepository {
public:
    explicit TradeRepository(std::shared_ptr<OracleConnectionPool> pool)
        : pool_(std::move(pool)), journalQueue_(1u << 16) {}

    // --- Hot-path API (wait-free enqueue) ----------------------------------
    // Called from trading threads; just pushes a POD copy and returns.
    void journalOrder(const oms::Order& o) noexcept {
        if (!journalQueue_.tryPush(o)) {
            // Production: spill to local WAL file rather than ever blocking/dropping.
        }
    }

    // --- Writer thread ------------------------------------------------------
    void startWriter();   // drains journalQueue_ and batch-inserts to Oracle
    void stopWriter();

    // --- Synchronous queries (dashboard / SOD / reports) -------------------
    [[nodiscard]] std::vector<oms::Order> loadOpenOrders();
    [[nodiscard]] std::int64_t loadPosition(SymbolId symbol);

private:
    void drainAndFlush();   // batch up to N records, single executeBatch+commit

    std::shared_ptr<OracleConnectionPool> pool_;
    lockfree::SPSCQueue<oms::Order>       journalQueue_;
    bool                                  writerRunning_{false};
    // Production: std::thread writer_; configurable batch size / flush interval.
};

}  // namespace rts::persistence
