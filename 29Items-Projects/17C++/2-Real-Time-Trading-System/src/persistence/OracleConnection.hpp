// ============================================================================
//  persistence/OracleConnection.hpp
//  Thin RAII wrapper over an Oracle OCCI/ODPI connection pool. The engine
//  journals orders/fills here ASYNCHRONOUSLY — the hot path enqueues PODs into
//  a bounded SPSC ring and a dedicated writer thread batches inserts. No
//  trading handler ever performs a synchronous DB round-trip.
// ============================================================================
#pragma once

#include <cstdint>
#include <memory>
#include <string>

#include "common/Config.hpp"

namespace rts::persistence {

// A pooled connection handle (returned to the pool on destruction).
class OracleConnection {
public:
    // Built under RTS_ENABLE_ORACLE: wrap oracle::occi::Connection* / dpiConn*.
    void executeBatch(const char* sql, std::size_t rows);  // batched DML
    void commit();
    void rollback();
};

class OracleConnectionPool {
public:
    explicit OracleConnectionPool(config::OracleConfig cfg) : cfg_(std::move(cfg)) {}

    void open();    // creates the OCCI environment + stateless connection pool
    void close();

    // Borrow/return a pooled connection (blocks only the writer thread).
    [[nodiscard]] std::shared_ptr<OracleConnection> acquire();

    [[nodiscard]] bool isOpen() const noexcept { return open_; }

private:
    config::OracleConfig cfg_;
    bool                 open_{false};
    // Built under RTS_ENABLE_ORACLE: oracle::occi::Environment*, oracle::occi::StatelessConnectionPool*
};

}  // namespace rts::persistence
