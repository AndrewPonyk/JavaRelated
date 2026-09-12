<?php

declare(strict_types=1);

namespace App\FraudDetection\Domain\Model;

use DateTimeImmutable;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Uid\Uuid;

/**
 * The persisted record of a fraud assessment for one order. Auditable and the
 * backing store for the manual-review queue (security-relevant; see ARCHITECTURE
 * §2.5). One assessment per order (the order id is unique).
 */
#[ORM\Entity]
#[ORM\Table(name: 'fraud_assessments')]
#[ORM\UniqueConstraint(name: 'uniq_fraud_order', columns: ['order_id'])]
#[ORM\Index(name: 'idx_fraud_status', columns: ['status'])]
class FraudAssessment
{
    public const string STATUS_PENDING = 'PENDING';
    public const string STATUS_RESOLVED = 'RESOLVED';
    public const string RESOLUTION_CLEARED = 'CLEARED';
    public const string RESOLUTION_CONFIRMED = 'CONFIRMED';

    #[ORM\Id]
    #[ORM\Column(type: Types::STRING, length: 36)]
    private string $id;

    #[ORM\Column(type: Types::STRING, length: 20)]
    private string $status = self::STATUS_PENDING;

    #[ORM\Column(type: Types::STRING, length: 20, nullable: true)]
    private ?string $resolution = null;

    #[ORM\Column(name: 'created_at', type: Types::DATETIME_IMMUTABLE)]
    private DateTimeImmutable $createdAt;

    #[ORM\Column(name: 'resolved_at', type: Types::DATETIME_IMMUTABLE, nullable: true)]
    private ?DateTimeImmutable $resolvedAt = null;

    public function __construct(
        #[ORM\Column(name: 'order_id', type: Types::STRING, length: 36)]
        private readonly string $orderId,
        #[ORM\Column(name: 'risk_score', type: Types::FLOAT)]
        private readonly float $riskScore,
        #[ORM\Column(type: Types::STRING, length: 20, enumType: FraudDecision::class)]
        private readonly FraudDecision $decision,
    ) {
        $this->id = Uuid::v7()->toRfc4122();
        $this->createdAt = new DateTimeImmutable();
    }

    /** A human resolution of the review. `$cleared` = legitimate (lift the hold). */
    public function resolve(bool $cleared): void
    {
        $this->status = self::STATUS_RESOLVED;
        $this->resolution = $cleared ? self::RESOLUTION_CLEARED : self::RESOLUTION_CONFIRMED;
        $this->resolvedAt = new DateTimeImmutable();
    }

    public function isResolved(): bool
    {
        return self::STATUS_RESOLVED === $this->status;
    }

    public function id(): string
    {
        return $this->id;
    }

    public function orderId(): string
    {
        return $this->orderId;
    }

    public function riskScore(): float
    {
        return $this->riskScore;
    }

    public function decision(): FraudDecision
    {
        return $this->decision;
    }

    public function status(): string
    {
        return $this->status;
    }

    public function resolution(): ?string
    {
        return $this->resolution;
    }

    public function createdAt(): DateTimeImmutable
    {
        return $this->createdAt;
    }
}
