<?php

declare(strict_types=1);

namespace App\FraudDetection\Application\DTO;

use App\FraudDetection\Domain\Model\FraudAssessment;

/** Read DTO for a queued fraud review. */
final readonly class FraudReviewView
{
    public function __construct(
        public string $orderId,
        public float $riskScore,
        public string $decision,
        public string $status,
        public string $createdAt,
    ) {
    }

    public static function fromAssessment(FraudAssessment $assessment): self
    {
        return new self(
            $assessment->orderId(),
            $assessment->riskScore(),
            $assessment->decision()->value,
            $assessment->status(),
            $assessment->createdAt()->format(\DATE_ATOM),
        );
    }

    /** @return array<string, mixed> */
    public function toArray(): array
    {
        return [
            'orderId' => $this->orderId,
            'riskScore' => $this->riskScore,
            'decision' => $this->decision,
            'status' => $this->status,
            'createdAt' => $this->createdAt,
        ];
    }
}
