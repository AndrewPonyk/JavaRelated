<?php

declare(strict_types=1);

namespace App\Payment\Application\Command;

use App\Payment\Domain\Event\PaymentCaptured;
use App\Payment\Domain\Gateway\PaymentDeclinedException;
use App\Payment\Domain\Gateway\PaymentGateway;
use App\Payment\Domain\Model\PaymentBlockedException;
use App\Payment\Domain\Model\Transaction;
use App\Payment\Domain\Repository\PaymentHoldRepository;
use App\Payment\Domain\Repository\TransactionRepository;
use App\Shared\Domain\Bus\Event\EventBus;
use App\Shared\Domain\ValueObject\Money;
use DateTimeImmutable;
use Psr\Log\LoggerInterface;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;
use Symfony\Component\Uid\Uuid;

/**
 * Captures payment for an order.
 *
 *  1. Idempotency — a prior capture with the same key is a no-op (never charges
 *     twice; safe under client/network retries).
 *  2. Fraud gate — refuse capture while a fraud BLOCK hold exists for the order.
 *  3. Capture via the PSP gateway; a decline is recorded as a FAILED transaction
 *     (not an exception that rolls everything back) so the outcome is auditable.
 *  4. On success, emit {@see PaymentCaptured}; the Ordering context transitions
 *     the order to PAID asynchronously.
 */
#[AsMessageHandler(bus: 'command.bus')]
final readonly class CapturePaymentHandler
{
    public function __construct(
        private TransactionRepository $transactions,
        private PaymentHoldRepository $holds,
        private PaymentGateway $gateway,
        private EventBus $eventBus,
        private LoggerInterface $logger,
    ) {
    }

    public function __invoke(CapturePayment $command): void
    {
        if (null !== $this->transactions->findByIdempotencyKey($command->idempotencyKey)) {
            return; // already processed — idempotent replay
        }

        if ($this->holds->existsForOrder($command->orderId)) {
            throw PaymentBlockedException::forOrder($command->orderId);
        }

        $transaction = new Transaction(
            id: Uuid::v7()->toRfc4122(),
            orderId: $command->orderId,
            customerId: $command->customerId,
            amountMinor: $command->amountMinor,
            currency: $command->currency,
            idempotencyKey: $command->idempotencyKey,
        );
        $this->transactions->add($transaction);

        try {
            $pspReference = $this->gateway->capture(
                $command->orderId,
                new Money($command->amountMinor, $command->currency),
                $command->paymentMethodToken,
            );
        } catch (PaymentDeclinedException $e) {
            // Record the failed attempt (committed with the transaction) and stop;
            // the UI surfaces the FAILED status. Do not rethrow — that would roll
            // back the very record we want to keep.
            $transaction->markFailed();
            $this->logger->warning('Payment declined', [
                'orderId' => $command->orderId,
                'reason' => $e->getMessage(),
            ]);

            return;
        }

        $transaction->markCaptured($pspReference);

        $this->eventBus->publish(new PaymentCaptured(
            orderId: $command->orderId,
            transactionId: $transaction->id(),
            amountMinor: $command->amountMinor,
            currency: $command->currency,
            pspReference: $pspReference,
            occurredOn: new DateTimeImmutable(),
        ));
    }
}
