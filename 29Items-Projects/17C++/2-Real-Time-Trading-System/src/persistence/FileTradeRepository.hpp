// ============================================================================
//  persistence/FileTradeRepository.hpp
//  Default ITradeRepository: writes orders/fills/predictions as JSON Lines to a
//  journal directory. Buffered, append-only — a real, queryable audit trail
//  that needs no database server. The Oracle adapter is the production swap-in.
// ============================================================================
#pragma once

#include <cstdint>
#include <fstream>
#include <string>

#include "persistence/ITradeRepository.hpp"

namespace rts::persistence {

class FileTradeRepository final : public ITradeRepository {
public:
    explicit FileTradeRepository(std::string journalDir);
    ~FileTradeRepository() override;

    void journalOrder(const oms::Order& o) override;
    void journalFill(const oms::ExecutionReport& er) override;
    void journalPrediction(const ml::PredictionResult& p, Nanos ts) override;
    void flush() override;

    // Counters for verification / tests.
    [[nodiscard]] std::uint64_t orderRecords()      const noexcept { return orders_; }
    [[nodiscard]] std::uint64_t fillRecords()       const noexcept { return fills_; }
    [[nodiscard]] std::uint64_t predictionRecords() const noexcept { return predictions_; }

private:
    std::string   dir_;
    std::ofstream ordersOut_;
    std::ofstream fillsOut_;
    std::ofstream predictionsOut_;
    std::uint64_t orders_{0};
    std::uint64_t fills_{0};
    std::uint64_t predictions_{0};
};

}  // namespace rts::persistence
