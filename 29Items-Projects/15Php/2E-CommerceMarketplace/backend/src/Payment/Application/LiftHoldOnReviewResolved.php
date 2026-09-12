<?php

declare(strict_types=1);

namespace App\Payment\Application;

use App\FraudDetection\Domain\Event\FraudReviewResolved;
use App\Payment\Domain\Repository\PaymentHoldRepository;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * When a reviewer clears a flagged order, lift the payment hold so the customer
 * can retry capture. A "confirmed fraud" resolution leaves the hold in place.
 */
#[AsMessageHandler(bus: 'event.bus')]
final readonly class LiftHoldOnReviewResolved
{
    public function __construct(private PaymentHoldRepository $holds)
    {
    }

    public function __invoke(FraudReviewResolved $event): void
    {
        if ($event->cleared) {
            $this->holds->removeForOrder($event->orderId);
        }
    }
}
