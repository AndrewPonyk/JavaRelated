// ============================================================================
//  apps/backtest/main.cpp
//  Runs a strategy over historical/synthetic ticks and reports PnL + drawdown.
//  Usage: backtest [ticks.csv] [steps]
// ============================================================================
#include <cstdio>
#include <memory>
#include <string>

#include "backtest/BacktestEngine.hpp"
#include "common/Logger.hpp"
#include "marketdata/MarketDataSource.hpp"
#include "strategy/strategies/MeanReversionStrategy.hpp"

using namespace rts;

int main(int argc, char** argv) {
    const std::string data = (argc > 1) ? argv[1] : "";
    const std::size_t steps = (argc > 2) ? static_cast<std::size_t>(std::stoul(argv[2]))
                                         : 8000;
    const SymbolId sym = static_cast<SymbolId>(1);

    log::Logger::instance().start();

    auto strat = std::make_shared<strategy::MeanReversionStrategy>(
        static_cast<StrategyId>(1), sym, Qty{100});
    backtest::BacktestEngine bt(strat, sym);

    const auto ticks = data.empty() ? md::generateSynthetic(sym, steps)
                                    : md::readCsv(data);
    const backtest::BacktestResult r = bt.runTicks(ticks);

    std::printf("\n==================== Backtest result ====================\n");
    std::printf("  ticks         : %llu\n", static_cast<unsigned long long>(r.ticks));
    std::printf("  orders        : %lld\n", static_cast<long long>(r.orders));
    std::printf("  fills         : %lld\n", static_cast<long long>(r.fills));
    std::printf("  final position: %lld\n", static_cast<long long>(r.finalPosition));
    std::printf("  realized PnL  : %.4f\n", r.realizedPnl);
    std::printf("  max drawdown  : %.4f\n", r.maxDrawdown);
    std::printf("=========================================================\n");

    log::Logger::instance().stop();
    return 0;
}
