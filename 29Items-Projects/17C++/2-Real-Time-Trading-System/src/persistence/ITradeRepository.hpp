// ============================================================================
//  persistence/ITradeRepository.hpp
//  The system-of-record seam. The engine depends only on this interface; the
//  default build uses FileTradeRepository (JSON Lines on disk), while the
//  production deployment swaps in the Oracle-backed adapter (see
//  OracleConnection.hpp / TradeRepository.hpp) without touching engine code.
// ============================================================================
#pragma once

#include "ml/Predictor.hpp"
#include "oms/Order.hpp"

namespace rts::persistence {

class ITradeRepository {
public:
    virtual ~ITradeRepository() = default;

    // Append a durable record of an order at its current state.
    virtual void journalOrder(const oms::Order& o) = 0;

    // Append a durable record of an execution (fill / state change).
    virtual void journalFill(const oms::ExecutionReport& er) = 0;

    // Append an advisory ML prediction (for post-trade model monitoring).
    virtual void journalPrediction(const ml::PredictionResult& p, Nanos ts) = 0;

    // Flush any buffered records to durable storage.
    virtual void flush() = 0;
};

}  // namespace rts::persistence
