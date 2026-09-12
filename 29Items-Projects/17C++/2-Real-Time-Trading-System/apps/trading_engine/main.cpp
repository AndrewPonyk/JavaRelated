// ============================================================================
//  apps/trading_engine/main.cpp
//  Entry point for the trading engine. Wires the engine to the in-process FIX
//  simulated exchange, drives a market-data session (synthetic or replayed CSV),
//  and prints a session summary. Everything journals to var/journal/.
//
//  Usage: trading_engine [config.yaml] [ticks.csv] [steps]
//    - config.yaml : engine config (default config/trading_engine.yaml; if the
//                    file is absent, validated defaults are used)
//    - ticks.csv   : optional recorded feed; if omitted a deterministic
//                    synthetic feed of `steps` moves is generated
//    - steps       : synthetic feed length (default 5000)
// ============================================================================
#include <atomic>
#include <csignal>
#include <cstdio>
#include <memory>
#include <string>
#include <vector>

#include "common/Config.hpp"
#include "common/Logger.hpp"
#include "engine/Engine.hpp"
#include "exchange/SimulatedExchange.hpp"
#include "marketdata/MarketDataSource.hpp"
#include "ml/Predictor.hpp"
#include "persistence/FileTradeRepository.hpp"
#include "strategy/strategies/MeanReversionStrategy.hpp"

using namespace rts;

namespace {
std::atomic<bool> g_running{true};
void onSignal(int) { g_running.store(false, std::memory_order_release); }
}  // namespace

int main(int argc, char** argv) {
    std::signal(SIGINT, onSignal);
    std::signal(SIGTERM, onSignal);

    const std::string cfgPath  = (argc > 1) ? argv[1] : "config/trading_engine.yaml";
    const std::string dataPath = (argc > 2) ? argv[2] : "";
    const std::size_t steps    = (argc > 3) ? static_cast<std::size_t>(std::stoul(argv[3]))
                                            : 5000;

    log::Logger::instance().start();
    try {
        const config::EngineConfig cfg = config::EngineConfig::load(cfgPath);
        RTS_INFO("config loaded");

        persistence::FileTradeRepository repo("var/journal");
        ml::LocalPredictor predictor;

        engine::Engine        eng(cfg, repo, predictor);
        exchange::SimulatedExchange exch(cfg.fix.targetCompId, cfg.fix.senderCompId);

        // In-process "wire": engine FIX out -> exchange, exchange out -> engine.
        eng.setOrderWire([&](const char* d, std::size_t l) { exch.onBytes(d, l); });
        exch.setWire([&](const char* d, std::size_t l) { eng.onExchangeBytes(d, l); });

        const SymbolId sym =
            cfg.instruments.empty()
                ? static_cast<SymbolId>(1)
                : static_cast<SymbolId>(cfg.instruments.front().symbolId);

        eng.addStrategy(
            std::make_shared<strategy::MeanReversionStrategy>(
                static_cast<StrategyId>(1), sym, Qty{100}),
            std::vector<SymbolId>{sym});

        eng.connect();
        if (!eng.loggedOn()) {
            RTS_ERROR("engine failed to log on to exchange");
            log::Logger::instance().stop();
            return 1;
        }

        const std::vector<md::Tick> ticks =
            dataPath.empty() ? md::generateSynthetic(sym, steps)
                             : md::readCsv(dataPath);
        RTS_INFO("feeding ticks", static_cast<std::int64_t>(ticks.size()));

        for (const auto& t : ticks) {
            if (!g_running.load(std::memory_order_acquire)) break;
            exch.onTick(t);   // venue sees market first so it can match at touch
            eng.onTick(t);    // engine reacts
        }

        eng.stop();

        const auto s = eng.stats();
        auto u = [](std::uint64_t v) { return static_cast<unsigned long long>(v); };
        std::printf("\n==================== Session summary ====================\n");
        std::printf("  market data : ticks=%llu  bookUpdates=%llu\n",
                    u(s.ticks), u(s.bookUpdates));
        std::printf("  predictions : %llu\n", u(s.predictions));
        std::printf("  orders      : sent=%llu  rejects=%llu\n",
                    u(s.ordersSent), u(s.rejects));
        std::printf("  fills       : %llu (partials=%llu)\n",
                    u(s.fills), u(s.partialFills));
        std::printf("  exchange    : received=%llu  execsSent=%llu\n",
                    u(exch.ordersReceived()), u(exch.execsSent()));
        std::printf("  position    : %lld\n",
                    static_cast<long long>(eng.position(sym)));
        std::printf("  PnL (m2m)   : %.4f\n", eng.markToMarketPnl());
        std::printf("  journal     : orders=%llu fills=%llu predictions=%llu -> var/journal/\n",
                    u(repo.orderRecords()), u(repo.fillRecords()), u(repo.predictionRecords()));
        std::printf("========================================================\n");

        log::Logger::instance().stop();
        return 0;
    } catch (const std::exception& e) {
        std::fprintf(stderr, "FATAL: %s\n", e.what());
        log::Logger::instance().stop();
        return 1;
    }
}
