<?php

declare(strict_types=1);

namespace App\Ordering\Domain\Repository;

use App\Ordering\Domain\Model\Order;
use App\Ordering\Domain\Model\OrderId;

/**
 * Port for the event-sourced Order persistence. The implementation appends new
 * events to the aggregate's stream (with optimistic concurrency) and rebuilds
 * the aggregate by replaying its stream.
 */
interface OrderEventStoreInterface
{
    /**
     * Append the aggregate's newly-recorded events.
     *
     * @throws ConcurrencyException if the stream
     *                              changed since it was loaded (expected vs actual version mismatch)
     */
    public function save(Order $order): void;

    /** Rebuild an order by replaying its event stream (using a snapshot if present). */
    public function load(OrderId $id): Order;
}
