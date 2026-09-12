<?php

declare(strict_types=1);

namespace App\Ordering\Infrastructure\EventStore;

use DateTimeImmutable;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

/**
 * One row per domain event in an aggregate's stream. This is an *infrastructure*
 * entity (not part of the domain), so attribute mapping here is appropriate.
 *
 * The unique (aggregate_id, version) constraint provides optimistic concurrency:
 * two writers appending the same next version collide at the database.
 */
#[ORM\Entity]
#[ORM\Table(name: 'order_event_store')]
#[ORM\UniqueConstraint(name: 'uniq_stream_version', columns: ['aggregate_id', 'version'])]
#[ORM\Index(name: 'idx_event_store_aggregate', columns: ['aggregate_id'])]
class StoredEvent
{
    #[ORM\Id]
    #[ORM\Column(type: Types::BIGINT)]
    #[ORM\GeneratedValue(strategy: 'IDENTITY')]
    private ?int $sequence = null;

    /**
     * @param array<string, mixed> $payload
     */
    public function __construct(
        #[ORM\Column(name: 'aggregate_id', type: Types::STRING, length: 36)]
        public readonly string $aggregateId,
        #[ORM\Column(type: Types::INTEGER)]
        public readonly int $version,
        #[ORM\Column(name: 'event_name', type: Types::STRING, length: 100)]
        public readonly string $eventName,
        #[ORM\Column(type: Types::JSON)]
        public readonly array $payload,
        #[ORM\Column(name: 'occurred_on', type: Types::DATETIME_IMMUTABLE)]
        public readonly DateTimeImmutable $occurredOn,
    ) {
    }

    public function sequence(): ?int
    {
        return $this->sequence;
    }
}
