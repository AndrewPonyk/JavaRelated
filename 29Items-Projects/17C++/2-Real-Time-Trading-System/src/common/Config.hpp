// ============================================================================
//  common/Config.hpp
//  Typed configuration loaded from YAML (config/trading_engine.yaml) with
//  environment-variable / Vault overrides. Validated at startup; the engine
//  refuses to run on an invalid config (fail fast).
// ============================================================================
#pragma once

#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace rts::config {

struct FixConfig {
    std::string host;
    std::uint16_t port{0};
    std::string senderCompId;
    std::string targetCompId;
    int heartbeatSecs{30};
};

struct OracleConfig {
    std::string host;
    std::uint16_t port{1521};
    std::string service;
    std::string user;
    std::string password;     // injected from Vault, never from the file
    int poolMin{2};
    int poolMax{8};
};

struct MlConfig {
    std::string grpcEndpoint{"localhost:50051"};
    std::string modelVersion{"lstm-v3"};
    int timeoutMs{20};        // advisory only; never blocks trading
    bool enabled{true};
};

struct RiskConfig {
    std::int64_t maxOrderQty{10'000};
    std::int64_t maxPositionQty{100'000};
    double maxNotional{5'000'000.0};
    double priceCollarPct{0.05};   // reject orders > 5% through the touch
};

struct InstrumentCfg {
    std::uint32_t symbolId{0};
    std::string   ticker;
    std::string   venue;
};

struct EngineConfig {
    std::string environment{"dev"};      // dev | staging | prod
    std::vector<unsigned> tradingCores;  // pinned core ids
    bool useHugePages{true};
    std::string logLevel{"INFO"};

    FixConfig    fix;
    OracleConfig oracle;
    MlConfig     ml;
    RiskConfig   risk;

    std::size_t  ringSize{1u << 20};
    std::string  waitStrategy{"busy_spin"};
    std::vector<InstrumentCfg> instruments;

    // Loads YAML + applies env overrides. Throws on missing/invalid required keys.
    // If the file is absent, returns validated defaults (useful for tests/CI).
    static EngineConfig load(const std::string& path);

    // Validates invariants; throws std::invalid_argument on violation.
    void validate() const {
        if (fix.host.empty())          throw std::invalid_argument("fix.host required");
        if (fix.senderCompId.empty())  throw std::invalid_argument("fix.senderCompId required");
        if (risk.maxOrderQty <= 0)     throw std::invalid_argument("risk.maxOrderQty > 0");
        if (oracle.poolMax < oracle.poolMin)
            throw std::invalid_argument("oracle.poolMax >= poolMin");
        // Core-id validation against host topology happens in tune_host.sh.
    }
};

}  // namespace rts::config
