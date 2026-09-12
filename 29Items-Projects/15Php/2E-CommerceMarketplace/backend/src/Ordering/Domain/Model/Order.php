<?php

declare(strict_types=1);

namespace App\Ordering\Domain\Model;

use App\Ordering\Domain\Event\OrderPaid;
use App\Ordering\Domain\Event\OrderPlaced;
use App\Shared\Domain\Aggregate\EventSourcedAggregateRoot;
use App\Shared\Domain\ValueObject\Money;
use DateTimeImmutable;
use DomainException;

/**
 * Order aggregate — EVENT SOURCED.
 *
 * The order's history *is* the source of truth (requirement: "event sourcing for
 * order history"). State below is never persisted directly; it is rebuilt by
 * folding the event stream via the apply*() mutators. Commands validate against
 * the current (folded) state, then record a new event.
 */
final class Order extends EventSourcedAggregateRoot
{
    private OrderId $id;
    private string $customerId;
    private OrderStatus $status;
    private Money $total;
    private ?string $paymentReference = null;
    /** @var list<array{productId: string, sellerId: string, quantity: int, unitPriceMinor: int}> */
    private array $lines = [];

    /**
     * Place a new order.
     *
     * @param list<OrderLine> $lines
     */
    public static function place(OrderId $id, string $customerId, array $lines): self
    {
        if ([] === $lines) {
            throw new DomainException('Cannot place an order with no lines.');
        }

        $currency = $lines[0]->unitPrice->currency;
        $total = array_reduce(
            $lines,
            static fn (Money $carry, OrderLine $l): Money => $carry->add($l->lineTotal()),
            Money::zero($currency),
        );

        $order = new self();
        $order->recordThat(new OrderPlaced(
            orderId: (string) $id,
            customerId: $customerId,
            currency: $currency,
            lines: array_map(static fn (OrderLine $l): array => [
                'productId' => $l->productId,
                'sellerId' => $l->sellerId,
                'quantity' => $l->quantity,
                'unitPriceMinor' => $l->unitPrice->amountMinor,
            ], $lines),
            totalMinor: $total->amountMinor,
            occurredOn: new DateTimeImmutable(),
        ));

        return $order;
    }

    /** Mark the order paid. Idempotency/validity is enforced against folded state. */
    public function markPaid(string $paymentReference): void
    {
        if (OrderStatus::Paid === $this->status) {
            return; // already paid — no-op (idempotent)
        }
        if (OrderStatus::Cancelled === $this->status) {
            throw new DomainException('Cannot pay a cancelled order.');
        }

        $this->recordThat(new OrderPaid((string) $this->id, $paymentReference, new DateTimeImmutable()));
    }

    public function id(): OrderId
    {
        return $this->id;
    }

    public function customerId(): string
    {
        return $this->customerId;
    }

    public function status(): OrderStatus
    {
        return $this->status;
    }

    public function total(): Money
    {
        return $this->total;
    }

    public function paymentReference(): ?string
    {
        return $this->paymentReference;
    }

    /**
     * @return list<array{productId: string, sellerId: string, quantity: int, unitPriceMinor: int}>
     */
    public function lines(): array
    {
        return $this->lines;
    }

    // ---- Event mutators (called during fold; must not contain business rules) --

    protected function applyOrderPlaced(OrderPlaced $event): void
    {
        $this->id = OrderId::fromString($event->orderId);
        $this->customerId = $event->customerId;
        $this->total = new Money($event->totalMinor, $event->currency);
        $this->lines = $event->lines;
        $this->status = OrderStatus::Pending;
    }

    protected function applyOrderPaid(OrderPaid $event): void
    {
        $this->status = OrderStatus::Paid;
        $this->paymentReference = $event->paymentReference;
    }
}
