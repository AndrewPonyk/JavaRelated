<?php

declare(strict_types=1);

namespace App\FraudDetection\Application;

use App\FraudDetection\Domain\Event\OrderFlagged;
use App\FraudDetection\Domain\Model\FraudAssessment;
use App\FraudDetection\Domain\Model\FraudDecision;
use App\FraudDetection\Domain\Repository\FraudAssessmentRepository;
use App\Ordering\Domain\Event\OrderPlaced;
use App\Shared\Domain\Bus\Event\EventBus;
use DateTimeImmutable;
use Psr\Log\LoggerInterface;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * Reacts to {@see OrderPlaced} (delivered asynchronously via RabbitMQ) and
 * assesses the transaction's fraud risk by calling the ML service through the
 * {@see FraudScorer} port.
 *
 * Runs OFF the order critical path so a slow/down model never blocks checkout.
 * The scorer fails OPEN to REVIEW, never silently allowing nor hard-failing. The
 * assessment is persisted (auditable + the review-queue source) and, when the
 * decision is not ALLOW, an {@see OrderFlagged} event is emitted so Payment can
 * hold capture and reviewers can act. Idempotent under at-least-once delivery.
 */
#[AsMessageHandler(bus: 'event.bus')]
final readonly class AssessOrderRiskOnOrderPlaced
{
    public function __construct(
        private FraudScorer $scorer,
        private FraudAssessmentRepository $assessments,
        private EventBus $eventBus,
        private LoggerInterface $logger,
        private float $blockThreshold,
    ) {
    }

    public function __invoke(OrderPlaced $event): void
    {
        if (null !== $this->assessments->findByOrderId($event->orderId)) {
            return; // already assessed
        }

        $features = [
            'amount_minor' => $event->totalMinor,
            'item_count' => array_sum(array_column($event->lines, 'quantity')),
            'distinct_sellers' => \count(array_unique(array_column($event->lines, 'sellerId'))),
            'currency' => $event->currency,
        ];

        $score = $this->scorer->score($features);
        $decision = $score->decisionAgainst($this->blockThreshold);

        $this->assessments->add(new FraudAssessment($event->orderId, $score->value, $decision));

        $this->logger->info('Fraud assessment completed', [
            'orderId' => $event->orderId,
            'risk' => $score->value,
            'decision' => $decision->value,
        ]);

        if (FraudDecision::Allow !== $decision) {
            $this->eventBus->publish(new OrderFlagged(
                orderId: $event->orderId,
                decision: $decision,
                riskScore: $score->value,
                occurredOn: new DateTimeImmutable(),
            ));
        }
    }
}
