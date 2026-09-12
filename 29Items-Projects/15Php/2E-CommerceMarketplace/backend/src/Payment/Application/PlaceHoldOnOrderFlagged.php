<?php

declare(strict_types=1);

namespace App\Payment\Application;

use App\FraudDetection\Domain\Event\OrderFlagged;
use App\FraudDetection\Domain\Model\FraudDecision;
use App\Payment\Domain\Model\PaymentHold;
use App\Payment\Domain\Repository\PaymentHoldRepository;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * Cross-context subscriber: when FraudDetection flags an order with a BLOCK
 * decision, place a hold so {@see Command\CapturePaymentHandler}
 * refuses capture. Idempotent under at-least-once delivery.
 */
#[AsMessageHandler(bus: 'event.bus')]
final readonly class PlaceHoldOnOrderFlagged
{
    public function __construct(private PaymentHoldRepository $holds)
    {
    }

    public function __invoke(OrderFlagged $event): void
    {
        if (FraudDecision::Block !== $event->decision) {
            return; // REVIEW is handled by the manual-review queue, not a hard hold
        }
        if ($this->holds->existsForOrder($event->orderId)) {
            return;
        }

        $this->holds->add(new PaymentHold(
            $event->orderId,
            \sprintf('fraud %s (risk %.2f)', $event->decision->value, $event->riskScore),
        ));
    }
}
