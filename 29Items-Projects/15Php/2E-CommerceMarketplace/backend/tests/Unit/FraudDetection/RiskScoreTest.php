<?php

declare(strict_types=1);

namespace App\Tests\Unit\FraudDetection;

use App\FraudDetection\Domain\Model\FraudDecision;
use App\FraudDetection\Domain\Model\RiskScore;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Test;
use PHPUnit\Framework\TestCase;

final class RiskScoreTest extends TestCase
{
    #[Test]
    public function a_low_score_is_allowed(): void
    {
        self::assertSame(FraudDecision::Allow, (new RiskScore(0.10))->decisionAgainst(0.85));
    }

    #[Test]
    public function a_mid_score_routes_to_review(): void
    {
        self::assertSame(FraudDecision::Review, (new RiskScore(0.60))->decisionAgainst(0.85));
    }

    #[Test]
    public function a_high_score_is_blocked(): void
    {
        self::assertSame(FraudDecision::Block, (new RiskScore(0.90))->decisionAgainst(0.85));
    }

    #[Test]
    public function the_block_threshold_is_inclusive(): void
    {
        self::assertSame(FraudDecision::Block, (new RiskScore(0.85))->decisionAgainst(0.85));
    }

    #[Test]
    public function it_rejects_scores_outside_the_unit_interval(): void
    {
        $this->expectException(InvalidArgumentException::class);

        new RiskScore(1.5);
    }
}
