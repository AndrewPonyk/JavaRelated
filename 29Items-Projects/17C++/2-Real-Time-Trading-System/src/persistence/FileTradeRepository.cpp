// ============================================================================
//  persistence/FileTradeRepository.cpp
//  JSON Lines journal writer. One file each for orders, fills, and predictions.
// ============================================================================
#include "persistence/FileTradeRepository.hpp"

#include <filesystem>

#include "common/Logger.hpp"

namespace rts::persistence {

namespace {
char sideChar(Side s) { return s == Side::Buy ? 'B' : 'S'; }
}  // namespace

FileTradeRepository::FileTradeRepository(std::string journalDir)
    : dir_(std::move(journalDir)) {
    std::error_code ec;
    std::filesystem::create_directories(dir_, ec);
    ordersOut_.open(dir_ + "/orders.jsonl", std::ios::app);
    fillsOut_.open(dir_ + "/fills.jsonl", std::ios::app);
    predictionsOut_.open(dir_ + "/predictions.jsonl", std::ios::app);
    if (!ordersOut_ || !fillsOut_ || !predictionsOut_) {
        RTS_ERROR("FileTradeRepository: failed to open journal files");
    }
}

FileTradeRepository::~FileTradeRepository() { flush(); }

void FileTradeRepository::journalOrder(const oms::Order& o) {
    ordersOut_ << "{\"id\":" << static_cast<std::uint64_t>(o.id)
               << ",\"strategy\":" << static_cast<std::uint32_t>(o.strategy)
               << ",\"symbol\":" << static_cast<std::uint32_t>(o.symbol)
               << ",\"side\":\"" << sideChar(o.side) << "\""
               << ",\"type\":" << static_cast<int>(o.type)
               << ",\"price\":" << o.price.toDouble()
               << ",\"qty\":" << o.qty.lots
               << ",\"filled\":" << o.filled.lots
               << ",\"state\":" << static_cast<int>(o.state)
               << ",\"ts\":" << o.lastUpdateTs << "}\n";
    ++orders_;
}

void FileTradeRepository::journalFill(const oms::ExecutionReport& er) {
    fillsOut_ << "{\"orderId\":" << static_cast<std::uint64_t>(er.orderId)
              << ",\"state\":" << static_cast<int>(er.newState)
              << ",\"lastQty\":" << er.lastQty.lots
              << ",\"lastPx\":" << er.lastPx.toDouble()
              << ",\"cumQty\":" << er.cumQty.lots
              << ",\"ts\":" << er.ts << "}\n";
    ++fills_;
}

void FileTradeRepository::journalPrediction(const ml::PredictionResult& p, Nanos ts) {
    predictionsOut_ << "{\"symbol\":" << static_cast<std::uint32_t>(p.symbol)
                    << ",\"signal\":" << p.signal
                    << ",\"confidence\":" << p.confidence
                    << ",\"ts\":" << ts << "}\n";
    ++predictions_;
}

void FileTradeRepository::flush() {
    ordersOut_.flush();
    fillsOut_.flush();
    predictionsOut_.flush();
}

}  // namespace rts::persistence
