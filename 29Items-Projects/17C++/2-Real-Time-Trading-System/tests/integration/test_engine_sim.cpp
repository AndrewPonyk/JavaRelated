// ============================================================================
//  tests/integration/test_engine_sim.cpp
//  End-to-end: Engine + in-process FIX SimulatedExchange + synthetic feed.
//  Exercises tick -> book -> strategy -> risk -> OMS -> FIX -> exchange -> fill
//  -> journal across the real codec, with the same code paths the app uses.
// ============================================================================
#include "engine/Engine.hpp"
#include "exchange/SimulatedExchange.hpp"
#include "marketdata/MarketDataSource.hpp"
#include "ml/Predictor.hpp"
#include "persistence/ITradeRepository.hpp"
#include "strategy/strategies/MeanReversionStrategy.hpp"

#include <gtest/gtest.h>

#include <memory>
#include <vector>

using namespace rts;

namespace {
// In-memory repository so the test does no file I/O.
struct CountingRepository final : persistence::ITradeRepository {
    std::uint64_t orders = 0, fills = 0, predictions = 0;
    void journalOrder(const oms::Order&) override { ++orders; }
    void journalFill(const oms::ExecutionReport&) override { ++fills; }
    void journalPrediction(const ml::PredictionResult&, Nanos) override { ++predictions; }
    void flush() override {}
};

config::EngineConfig makeConfig() {
    config::EngineConfig cfg{};
    cfg.fix.senderCompId = "TRADER1";
    cfg.fix.targetCompId = "EXCHANGE";
    return cfg;
}
}  // namespace

TEST(EngineSim, LogsOnThroughFixHandshake) {
    CountingRepository repo;
    ml::LocalPredictor predictor;
    engine::Engine eng(makeConfig(), repo, predictor);
    exchange::SimulatedExchange exch("EXCHANGE", "TRADER1");

    eng.setOrderWire([&](const char* d, std::size_t l) { exch.onBytes(d, l); });
    exch.setWire([&](const char* d, std::size_t l) { eng.onExchangeBytes(d, l); });

    eng.connect();
    EXPECT_TRUE(eng.loggedOn());
    EXPECT_TRUE(exch.loggedOn());
}

TEST(EngineSim, ProducesOrdersFillsAndJournalThroughFullPipeline) {
    CountingRepository repo;
    ml::LocalPredictor predictor;
    engine::Engine eng(makeConfig(), repo, predictor);
    exchange::SimulatedExchange exch("EXCHANGE", "TRADER1");

    eng.setOrderWire([&](const char* d, std::size_t l) { exch.onBytes(d, l); });
    exch.setWire([&](const char* d, std::size_t l) { eng.onExchangeBytes(d, l); });

    const SymbolId sym = static_cast<SymbolId>(1);
    eng.addStrategy(std::make_shared<strategy::MeanReversionStrategy>(
                        static_cast<StrategyId>(1), sym, Qty{100}),
                    std::vector<SymbolId>{sym});
    eng.connect();
    ASSERT_TRUE(eng.loggedOn());

    const auto ticks = md::generateSynthetic(sym, 3000, /*seed=*/7);
    for (const auto& t : ticks) {
        exch.onTick(t);
        eng.onTick(t);
    }
    eng.stop();

    const auto s = eng.stats();
    EXPECT_GT(s.bookUpdates, 0u);
    EXPECT_GT(s.predictions, 0u);
    EXPECT_GT(s.ordersSent, 0u);
    EXPECT_GT(s.fills, 0u);
    EXPECT_EQ(s.rejects, 0u);

    // Every order the engine sent was received by the exchange over FIX.
    EXPECT_EQ(exch.ordersReceived(), s.ordersSent);
    // Each marketable order yields a partial fill + an IOC cancel = 2 reports.
    EXPECT_GE(exch.execsSent(), s.ordersSent);

    // Journaling captured order lifecycle + fills + predictions.
    EXPECT_GT(repo.orders, 0u);
    EXPECT_GT(repo.fills, 0u);
    EXPECT_GT(repo.predictions, 0u);
}

TEST(EngineSim, ReplaysRecordedCsvDeterministically) {
    const SymbolId sym = static_cast<SymbolId>(1);
    const auto generated = md::generateSynthetic(sym, 500, /*seed=*/99);
    const std::string path = "test_engine_ticks_tmp.csv";
    md::writeCsv(path, generated);
    const auto replayed = md::readCsv(path);
    std::remove(path.c_str());

    ASSERT_EQ(replayed.size(), generated.size());
    EXPECT_EQ(replayed.front().price, generated.front().price);
    EXPECT_EQ(replayed.back().qty, generated.back().qty);
}
