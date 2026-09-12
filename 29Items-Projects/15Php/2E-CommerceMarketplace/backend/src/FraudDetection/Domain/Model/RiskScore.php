<?php

declare(strict_types=1);

namespace App\FraudDetection\Domain\Model;

use InvalidArgumentException;

/** Normalised fraud risk in [0.0, 1.0]. 0 = benign, 1 = almost certainly fraud. */
final readonly class RiskScore
{
    public function __construct(public float $value)
    {
        if ($value < 0.0 || $value > 1.0) {
            throw new InvalidArgumentException('Risk score must be within [0, 1].');
        }
    }

    public function decisionAgainst(float $blockThreshold, float $reviewThreshold = 0.5): FraudDecision
    {
        return match (true) {
            $this->value >= $blockThreshold => FraudDecision::Block,
            $this->value >= $reviewThreshold => FraudDecision::Review,
            default => FraudDecision::Allow,
        };
    }
}
