<?php

declare(strict_types=1);

namespace App\Tests\Unit\Ordering;

use App\Ordering\Domain\Event\OrderPaid;
use App\Ordering\Domain\Event\OrderPlaced;
use App\Ordering\Domain\Model\Order;
use App\Ordering\Domain\Model\OrderId;
use App\Ordering\Domain\Model\OrderLine;
use App\Ordering\Domain\Model\OrderStatus;
use App\Shared\Domain\ValueObject\Money;
use DateTimeImmutable;
use PHPUnit\Framework\Attributes\Test;
use PHPUnit\Framework\TestCase;

/**
 * Event-sourcing tests follow given (past events) / when (command) / then
 * (new events + folded state). See docs/TECH-NOTES.md §3.2.
 */
final class OrderTest extends TestCase
{
    #[Test]
    public function placing_an_order_totals_the_lines_and_records_order_placed(): void
    {
        // when
        $order = Order::place(OrderId::generate(), 'customer-1', [
            new OrderLine('p1', 's1', 2, Money::of(1000, 'USD')),
            new OrderLine('p2', 's1', 1, Money::of(500, 'USD')),
        ]);

        // then
        $events = $order->pullDomainEvents();
        self::assertCount(1, $events);
        self::assertInstanceOf(OrderPlaced::class, $events[0]);
        self::assertSame(2500, $events[0]->totalMinor);
        self::assertSame(OrderStatus::Pending, $order->status());
    }

    #[Test]
    public function an_order_rebuilt_from_history_then_paid_becomes_paid(): void
    {
        // given a placed order, reconstituted from its event stream
        $orderId = (string) OrderId::generate();
        $history = [
            new OrderPlaced($orderId, 'customer-1', 'USD', [
                ['productId' => 'p1', 'sellerId' => 's1', 'quantity' => 1, 'unitPriceMinor' => 2500],
            ], 2500, new DateTimeImmutable()),
        ];
        $order = Order::reconstituteFromHistory($history);
        self::assertSame(1, $order->version());
        self::assertSame($orderId, (string) $order->id());

        // when
        $order->markPaid('pay_ref_123');

        // then
        $new = $order->pullDomainEvents();
        self::assertCount(1, $new);
        self::assertInstanceOf(OrderPaid::class, $new[0]);
        self::assertSame(OrderStatus::Paid, $order->status());
    }

    #[Test]
    public function paying_twice_is_idempotent(): void
    {
        $order = Order::place(OrderId::generate(), 'c1', [new OrderLine('p1', 's1', 1, Money::of(100, 'USD'))]);
        $order->pullDomainEvents(); // discard OrderPlaced

        $order->markPaid('ref');
        $order->pullDomainEvents(); // first payment recorded
        $order->markPaid('ref');    // second call is a no-op

        self::assertCount(0, $order->pullDomainEvents());
    }
}
