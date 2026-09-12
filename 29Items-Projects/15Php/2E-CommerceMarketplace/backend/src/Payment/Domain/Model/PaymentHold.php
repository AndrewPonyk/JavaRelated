<?php

declare(strict_types=1);

namespace App\Payment\Domain\Model;

use DateTimeImmutable;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

/**
 * A hold placed on an order's payment because FraudDetection returned a BLOCK
 * decision. Capture is refused while a hold exists. This is a small read model
 * populated by subscribing to the FraudDetection OrderFlagged integration event.
 */
#[ORM\Entity]
#[ORM\Table(name: 'payment_holds')]
class PaymentHold
{
    #[ORM\Column(name: 'created_at', type: Types::DATETIME_IMMUTABLE)]
    private DateTimeImmutable $createdAt;

    public function __construct(
        #[ORM\Id]
        #[ORM\Column(name: 'order_id', type: Types::STRING, length: 36)]
        private readonly string $orderId,
        #[ORM\Column(type: Types::STRING, length: 255)]
        private readonly string $reason,
    ) {
        $this->createdAt = new DateTimeImmutable();
    }

    public function orderId(): string
    {
        return $this->orderId;
    }

    public function reason(): string
    {
        return $this->reason;
    }
}
