<?php

declare(strict_types=1);

namespace App\Payment\Application\DTO;

use App\Payment\Domain\Model\Transaction;

/** Read DTO describing the outcome of a capture attempt. */
final readonly class PaymentView
{
    public function __construct(
        public string $transactionId,
        public string $orderId,
        public string $status,
        public int $amountMinor,
        public string $currency,
        public ?string $pspReference,
    ) {
    }

    public static function fromTransaction(Transaction $transaction): self
    {
        return new self(
            $transaction->id(),
            $transaction->orderId(),
            $transaction->status()->value,
            $transaction->amountMinor(),
            $transaction->currency(),
            $transaction->pspReference(),
        );
    }

    public function isCaptured(): bool
    {
        return 'CAPTURED' === $this->status;
    }

    /** @return array<string, mixed> */
    public function toArray(): array
    {
        return [
            'transactionId' => $this->transactionId,
            'orderId' => $this->orderId,
            'status' => $this->status,
            'amount' => ['amountMinor' => $this->amountMinor, 'currency' => $this->currency],
            'pspReference' => $this->pspReference,
        ];
    }
}
