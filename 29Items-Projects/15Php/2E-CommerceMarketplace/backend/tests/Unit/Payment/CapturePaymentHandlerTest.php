<?php

declare(strict_types=1);

namespace App\Tests\Unit\Payment;

use App\Payment\Application\Command\CapturePayment;
use App\Payment\Application\Command\CapturePaymentHandler;
use App\Payment\Domain\Event\PaymentCaptured;
use App\Payment\Domain\Model\PaymentBlockedException;
use App\Payment\Domain\Model\PaymentHold;
use App\Payment\Domain\Model\PaymentStatus;
use App\Payment\Infrastructure\Gateway\FakePaymentGateway;
use App\Tests\Support\InMemoryPaymentHoldRepository;
use App\Tests\Support\InMemoryTransactionRepository;
use App\Tests\Support\RecordingEventBus;
use PHPUnit\Framework\Attributes\Test;
use PHPUnit\Framework\TestCase;
use Psr\Log\NullLogger;
use Symfony\Component\Uid\Uuid;

final class CapturePaymentHandlerTest extends TestCase
{
    private InMemoryTransactionRepository $transactions;
    private InMemoryPaymentHoldRepository $holds;
    private RecordingEventBus $events;
    private CapturePaymentHandler $handler;

    protected function setUp(): void
    {
        $this->transactions = new InMemoryTransactionRepository();
        $this->holds = new InMemoryPaymentHoldRepository();
        $this->events = new RecordingEventBus();
        $this->handler = new CapturePaymentHandler(
            $this->transactions,
            $this->holds,
            new FakePaymentGateway(new NullLogger()),
            $this->events,
            new NullLogger(),
        );
    }

    #[Test]
    public function a_successful_capture_records_a_transaction_and_emits_payment_captured(): void
    {
        ($this->handler)($this->command());

        self::assertCount(1, $this->transactions->items);
        self::assertSame(PaymentStatus::Captured, $this->transactions->items[0]->status());
        self::assertNotNull($this->transactions->items[0]->pspReference());
        self::assertCount(1, $this->events->ofType(PaymentCaptured::class));
    }

    #[Test]
    public function a_repeated_idempotency_key_never_captures_twice(): void
    {
        $command = $this->command(idempotencyKey: 'key-1');

        ($this->handler)($command);
        ($this->handler)($command);

        self::assertCount(1, $this->transactions->items);
        self::assertCount(1, $this->events->ofType(PaymentCaptured::class));
    }

    #[Test]
    public function a_fraud_hold_blocks_capture(): void
    {
        $orderId = (string) Uuid::v7();
        $this->holds->add(new PaymentHold($orderId, 'fraud'));

        $this->expectException(PaymentBlockedException::class);

        try {
            ($this->handler)($this->command(orderId: $orderId));
        } finally {
            self::assertSame([], $this->transactions->items);
        }
    }

    #[Test]
    public function a_declined_card_records_a_failed_transaction_and_emits_nothing(): void
    {
        ($this->handler)($this->command(paymentMethodToken: 'tok_decline'));

        self::assertCount(1, $this->transactions->items);
        self::assertSame(PaymentStatus::Failed, $this->transactions->items[0]->status());
        self::assertSame([], $this->events->ofType(PaymentCaptured::class));
    }

    private function command(
        string $paymentMethodToken = 'tok_visa',
        ?string $idempotencyKey = null,
        ?string $orderId = null,
    ): CapturePayment {
        return new CapturePayment(
            orderId: $orderId ?? (string) Uuid::v7(),
            customerId: (string) Uuid::v7(),
            amountMinor: 2500,
            currency: 'USD',
            paymentMethodToken: $paymentMethodToken,
            idempotencyKey: $idempotencyKey ?? (string) Uuid::v7(),
        );
    }
}
