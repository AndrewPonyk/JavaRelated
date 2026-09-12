<?php

declare(strict_types=1);

namespace App\FraudDetection\Application\Query;

use App\FraudDetection\Application\DTO\FraudReviewView;
use App\Shared\Domain\Bus\Query\Query;

/**
 * @implements Query<list<FraudReviewView>>
 */
final readonly class ListPendingFraudReviews implements Query
{
    public function __construct(public int $limit = 50)
    {
    }
}
