<?php

declare(strict_types=1);

namespace App\FraudDetection\Application\Query;

use App\FraudDetection\Application\DTO\FraudReviewView;
use App\FraudDetection\Domain\Repository\FraudAssessmentRepository;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler(bus: 'query.bus')]
final readonly class ListPendingFraudReviewsHandler
{
    public function __construct(private FraudAssessmentRepository $assessments)
    {
    }

    /**
     * @return list<FraudReviewView>
     */
    public function __invoke(ListPendingFraudReviews $query): array
    {
        return array_map(
            FraudReviewView::fromAssessment(...),
            $this->assessments->listPending(min(200, max(1, $query->limit))),
        );
    }
}
