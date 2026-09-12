<?php

declare(strict_types=1);

namespace App\Ordering\Application\Query;

use App\Ordering\Application\DTO\OrderView;
use App\Ordering\Domain\Model\OrderId;
use App\Ordering\Domain\Repository\OrderEventStoreInterface;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * Reads an order by replaying its event stream — the event-sourced "order history
 * reconstructed from events" read path (see ARCHITECTURE §2.3). Ownership is
 * enforced at the UI boundary using the returned customerId.
 */
#[AsMessageHandler(bus: 'query.bus')]
final readonly class GetOrderHandler
{
    public function __construct(private OrderEventStoreInterface $orders)
    {
    }

    public function __invoke(GetOrder $query): OrderView
    {
        return OrderView::fromOrder($this->orders->load(OrderId::fromString($query->orderId)));
    }
}
