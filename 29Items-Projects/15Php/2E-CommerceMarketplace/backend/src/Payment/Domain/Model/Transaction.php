<?php

declare(strict_types=1);

namespace App\Payment\Domain\Model;

use DateTimeImmutable;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

/**
 * A payment transaction against an order. We store only a PSP reference/token —
 * never raw card data — to keep PCI scope minimal (see ARCHITECTURE §2.5). The
 * unique idempotency key makes client/network retries safe.
 */
#[ORM\Entity]
#[ORM\Table(name: 'payment_transactions')]
#[ORM\UniqueConstraint(name: 'uniq_payment_idempotency', columns: ['idempotency_key'])]
#[ORM\Index(name: 'idx_payment_order', columns: ['order_id'])]
class Transaction
{
    #[ORM\Id]
    #[ORM\Column(type: Types::STRING, length: 36)]
    private string $id;

    #[ORM\Column(type: Types::STRING, length: 20, enumType: PaymentStatus::class)]
    private PaymentStatus $status;

    #[ORM\Column(name: 'psp_reference', type: Types::STRING, length: 100, nullable: true)]
    private ?string $pspReference = null;

    #[ORM\Column(name: 'created_at', type: Types::DATETIME_IMMUTABLE)]
    private DateTimeImmutable $createdAt;

    public function __construct(
        string $id,
        #[ORM\Column(name: 'order_id', type: Types::STRING, length: 36)]
        private readonly string $orderId,
        #[ORM\Column(name: 'customer_id', type: Types::STRING, length: 36)]
        private readonly string $customerId,
        #[ORM\Column(name: 'amount_minor', type: Types::INTEGER)]
        private readonly int $amountMinor,
        #[ORM\Column(type: Types::STRING, length: 3)]
        private readonly string $currency,
        #[ORM\Column(name: 'idempotency_key', type: Types::STRING, length: 64)]
        private readonly string $idempotencyKey,
    ) {
        $this->id = $id;
        $this->status = PaymentStatus::Pending;
        $this->createdAt = new DateTimeImmutable();
    }

    public function markCaptured(string $pspReference): void
    {
        $this->status = PaymentStatus::Captured;
        $this->pspReference = $pspReference;
    }

    public function markFailed(): void
    {
        $this->status = PaymentStatus::Failed;
    }

    public function id(): string
    {
        return $this->id;
    }

    public function orderId(): string
    {
        return $this->orderId;
    }

    public function customerId(): string
    {
        return $this->customerId;
    }

    public function amountMinor(): int
    {
        return $this->amountMinor;
    }

    public function currency(): string
    {
        return $this->currency;
    }

    public function status(): PaymentStatus
    {
        return $this->status;
    }

    public function pspReference(): ?string
    {
        return $this->pspReference;
    }

    public function idempotencyKey(): string
    {
        return $this->idempotencyKey;
    }
}
