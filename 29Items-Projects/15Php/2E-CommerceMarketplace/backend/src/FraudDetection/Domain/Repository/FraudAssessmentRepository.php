<?php

declare(strict_types=1);

namespace App\FraudDetection\Domain\Repository;

use App\FraudDetection\Domain\Model\FraudAssessment;

interface FraudAssessmentRepository
{
    public function add(FraudAssessment $assessment): void;

    public function findByOrderId(string $orderId): ?FraudAssessment;

    /**
     * Pending (unresolved) assessments for the manual-review queue.
     *
     * @return list<FraudAssessment>
     */
    public function listPending(int $limit = 50): array;
}
