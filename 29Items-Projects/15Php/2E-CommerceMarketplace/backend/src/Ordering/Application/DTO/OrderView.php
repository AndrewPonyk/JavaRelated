<?php

declare(strict_types=1);

namespace App\Ordering\Application\DTO;

use App\Ordering\Domain\Model\Order;

/**
 * Read DTO for an order, projected from the event-sourced aggregate's current
 * (folded) state — i.e. the result of replaying its event stream.
 */
final readonly class OrderView
{
    /**
     * @param list<array{productId: string, sellerId: string, quantity: int, unitPriceMinor: int}> $lines
     */
    public function __construct(
        public string $id,
        public string $customerId,
        public string $status,
        public int $totalMinor,
        public string $currency,
        public ?string $paymentReference,
        public int $version,
        public array $lines,
    ) {
    }

    public static function fromOrder(Order $order): self
    {
        return new self(
            (string) $order->id(),
            $order->customerId(),
            $order->status()->value,
            $order->total()->amountMinor,
            $order->total()->currency,
            $order->paymentReference(),
            $order->version(),
            $order->lines(),
        );
    }

    /** @return array<string, mixed> */
    public function toArray(): array
    {
        return [
            'id' => $this->id,
            'customerId' => $this->customerId,
            'status' => $this->status,
            'total' => ['amountMinor' => $this->totalMinor, 'currency' => $this->currency],
            'paymentReference' => $this->paymentReference,
            'version' => $this->version,
            'lines' => $this->lines,
        ];
    }
}
