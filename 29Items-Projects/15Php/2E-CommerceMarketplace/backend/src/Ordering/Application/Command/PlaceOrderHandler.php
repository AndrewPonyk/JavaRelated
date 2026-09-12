<?php

declare(strict_types=1);

namespace App\Ordering\Application\Command;

use App\Ordering\Domain\Model\Order;
use App\Ordering\Domain\Model\OrderId;
use App\Ordering\Domain\Model\OrderLine;
use App\Ordering\Domain\Repository\OrderEventStoreInterface;
use App\Shared\Domain\ValueObject\Money;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler(bus: 'command.bus')]
final readonly class PlaceOrderHandler
{
    public function __construct(private OrderEventStoreInterface $orders)
    {
    }

    public function __invoke(PlaceOrder $command): void
    {
        $lines = array_map(
            static fn (array $l): OrderLine => new OrderLine(
                productId: $l['productId'],
                sellerId: $l['sellerId'],
                quantity: $l['quantity'],
                unitPrice: new Money($l['unitPriceMinor'], $command->currency),
            ),
            $command->lines,
        );

        $order = Order::place(OrderId::fromString($command->orderId), $command->customerId, $lines);

        // Appends events + publishes them (outbox). Fraud scoring & commission
        // accrual react asynchronously to the resulting OrderPlaced event.
        $this->orders->save($order);
    }
}
