<?php

declare(strict_types=1);

namespace App\FraudDetection\Application;

use App\FraudDetection\Domain\Model\RiskScore;

/**
 * Port to the fraud-scoring capability. The domain depends on this abstraction,
 * not on HTTP or the ML service — that detail lives behind the adapter
 * (HttpFraudScorer) in the Infrastructure layer (anti-corruption layer).
 */
interface FraudScorer
{
    /**
     * @param array<string, scalar> $features model input features extracted from
     *                                        the transaction (amount, item count, velocity, customer age, …)
     */
    public function score(array $features): RiskScore;
}
