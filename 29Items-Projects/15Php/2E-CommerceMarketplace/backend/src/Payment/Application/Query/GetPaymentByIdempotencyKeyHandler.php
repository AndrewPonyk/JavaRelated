<?php

declare(strict_types=1);

namespace App\Payment\Application\Query;

use App\Payment\Application\DTO\PaymentView;
use App\Payment\Domain\Repository\TransactionRepository;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler(bus: 'query.bus')]
final readonly class GetPaymentByIdempotencyKeyHandler
{
    public function __construct(private TransactionRepository $transactions)
    {
    }

    public function __invoke(GetPaymentByIdempotencyKey $query): ?PaymentView
    {
        $transaction = $this->transactions->findByIdempotencyKey($query->idempotencyKey);

        return null === $transaction ? null : PaymentView::fromTransaction($transaction);
    }
}
