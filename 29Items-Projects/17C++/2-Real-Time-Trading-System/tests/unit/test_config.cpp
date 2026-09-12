// ============================================================================
//  tests/unit/test_config.cpp
//  Config YAML-subset parser + env-override behavior.
// ============================================================================
#include "common/Config.hpp"

#include <gtest/gtest.h>

#include <cstdio>
#include <fstream>

using namespace rts::config;

namespace {
const char* kYaml =
    "environment: staging\n"
    "trading_cores: [2, 3, 4, 5]\n"
    "use_huge_pages: true\n"
    "log_level: WARN\n"
    "fix:\n"
    "  host: 10.0.0.7\n"
    "  port: 5050\n"
    "  sender_comp_id: ALPHA\n"
    "  target_comp_id: VENUE\n"
    "  heartbeat_secs: 15\n"
    "risk:\n"
    "  max_order_qty: 250\n"
    "  max_position_qty: 9000\n"
    "  max_notional: 1234567.0\n"
    "  price_collar_pct: 0.02\n"
    "ml:\n"
    "  enabled: true\n"
    "  grpc_endpoint: ml:9000\n"
    "  model_version: lstm-v9\n"
    "instruments:\n"
    "  - { symbol_id: 1, ticker: AAPL, venue: XNAS }\n"
    "  - { symbol_id: 2, ticker: ESZ5, venue: XCME }\n";

std::string writeTemp(const char* content) {
    const std::string path = "test_config_tmp.yaml";
    std::ofstream(path, std::ios::trunc) << content;
    return path;
}
}  // namespace

TEST(Config, ParsesNestedScalarsListsAndInstruments) {
    const std::string path = writeTemp(kYaml);
    const EngineConfig cfg = EngineConfig::load(path);
    std::remove(path.c_str());

    EXPECT_EQ(cfg.environment, "staging");
    EXPECT_EQ(cfg.logLevel, "WARN");
    EXPECT_TRUE(cfg.useHugePages);
    ASSERT_EQ(cfg.tradingCores.size(), 4u);
    EXPECT_EQ(cfg.tradingCores[0], 2u);
    EXPECT_EQ(cfg.tradingCores[3], 5u);

    EXPECT_EQ(cfg.fix.host, "10.0.0.7");
    EXPECT_EQ(cfg.fix.port, 5050);
    EXPECT_EQ(cfg.fix.senderCompId, "ALPHA");
    EXPECT_EQ(cfg.fix.targetCompId, "VENUE");
    EXPECT_EQ(cfg.fix.heartbeatSecs, 15);

    EXPECT_EQ(cfg.risk.maxOrderQty, 250);
    EXPECT_EQ(cfg.risk.maxPositionQty, 9000);
    EXPECT_NEAR(cfg.risk.maxNotional, 1234567.0, 1e-6);
    EXPECT_NEAR(cfg.risk.priceCollarPct, 0.02, 1e-9);

    EXPECT_EQ(cfg.ml.grpcEndpoint, "ml:9000");
    EXPECT_EQ(cfg.ml.modelVersion, "lstm-v9");

    ASSERT_EQ(cfg.instruments.size(), 2u);
    EXPECT_EQ(cfg.instruments[0].symbolId, 1u);
    EXPECT_EQ(cfg.instruments[0].ticker, "AAPL");
    EXPECT_EQ(cfg.instruments[1].venue, "XCME");
}

TEST(Config, MissingFileYieldsValidatedDefaults) {
    const EngineConfig cfg = EngineConfig::load("does_not_exist_12345.yaml");
    EXPECT_FALSE(cfg.fix.host.empty());          // default filled in
    EXPECT_FALSE(cfg.fix.senderCompId.empty());
    EXPECT_GT(cfg.risk.maxOrderQty, 0);          // validate() would have thrown otherwise
}
