<?php

declare(strict_types=1);

namespace App\Ordering\Application;

use App\Ordering\Domain\Model\OrderId;
use App\Ordering\Domain\Repository\OrderEventStoreInterface;
use App\Payment\Domain\Event\PaymentCaptured;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * Process manager: when Payment reports a capture, transition the order to PAID
 * by appending OrderPaid to its event stream. {@see \App\Ordering\Domain\Model\Order::markPaid()}
 * is idempotent, so at-least-once delivery (re-handling the same event) is safe —
 * a second delivery records no new event and {@see OrderEventStoreInterface::save()}
 * no-ops.
 */
#[AsMessageHandler(bus: 'event.bus')]
final readonly class MarkOrderPaidOnPaymentCaptured
{
    public function __construct(private OrderEventStoreInterface $orders)
    {
    }

    public function __invoke(PaymentCaptured $event): void
    {
        $order = $this->orders->load(OrderId::fromString($event->orderId));
        $order->markPaid($event->pspReference);
        $this->orders->save($order);
    }
}
