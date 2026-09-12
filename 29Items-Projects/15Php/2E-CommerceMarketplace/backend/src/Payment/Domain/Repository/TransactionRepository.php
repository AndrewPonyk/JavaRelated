<?php

declare(strict_types=1);

namespace App\Payment\Domain\Repository;

use App\Payment\Domain\Model\Transaction;

interface TransactionRepository
{
    public function add(Transaction $transaction): void;

    public function findByIdempotencyKey(string $idempotencyKey): ?Transaction;

    public function findLatestForOrder(string $orderId): ?Transaction;
}
