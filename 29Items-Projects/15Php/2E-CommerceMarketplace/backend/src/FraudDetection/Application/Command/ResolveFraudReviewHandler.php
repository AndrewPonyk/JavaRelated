<?php

declare(strict_types=1);

namespace App\FraudDetection\Application\Command;

use App\FraudDetection\Domain\Event\FraudReviewResolved;
use App\FraudDetection\Domain\Model\FraudAssessmentNotFoundException;
use App\FraudDetection\Domain\Repository\FraudAssessmentRepository;
use App\Shared\Domain\Bus\Event\EventBus;
use DateTimeImmutable;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler(bus: 'command.bus')]
final readonly class ResolveFraudReviewHandler
{
    public function __construct(
        private FraudAssessmentRepository $assessments,
        private EventBus $eventBus,
    ) {
    }

    public function __invoke(ResolveFraudReview $command): void
    {
        $assessment = $this->assessments->findByOrderId($command->orderId)
            ?? throw FraudAssessmentNotFoundException::forOrder($command->orderId);

        if ($assessment->isResolved()) {
            return; // already resolved — idempotent
        }

        $assessment->resolve($command->cleared);

        $this->eventBus->publish(new FraudReviewResolved(
            orderId: $command->orderId,
            cleared: $command->cleared,
            occurredOn: new DateTimeImmutable(),
        ));
    }
}
