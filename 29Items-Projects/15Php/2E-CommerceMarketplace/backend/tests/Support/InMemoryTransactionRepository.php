<?php

declare(strict_types=1);

namespace App\Tests\Support;

use App\Payment\Domain\Model\Transaction;
use App\Payment\Domain\Repository\TransactionRepository;

final class InMemoryTransactionRepository implements TransactionRepository
{
    /** @var list<Transaction> */
    public array $items = [];

    public function add(Transaction $transaction): void
    {
        $this->items[] = $transaction;
    }

    public function findByIdempotencyKey(string $idempotencyKey): ?Transaction
    {
        foreach ($this->items as $transaction) {
            if ($transaction->idempotencyKey() === $idempotencyKey) {
                return $transaction;
            }
        }

        return null;
    }

    public function findLatestForOrder(string $orderId): ?Transaction
    {
        $latest = null;
        foreach ($this->items as $transaction) {
            if ($transaction->orderId() === $orderId) {
                $latest = $transaction;
            }
        }

        return $latest;
    }
}
