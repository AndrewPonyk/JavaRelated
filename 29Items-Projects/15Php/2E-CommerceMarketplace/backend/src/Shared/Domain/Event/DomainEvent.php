<?php

declare(strict_types=1);

namespace App\Shared\Domain\Event;

use DateTimeImmutable;

/**
 * Marker/contract for all domain events.
 *
 * Domain events are immutable facts about something that happened in the past.
 * They are serialised onto the event bus (and, for event-sourced aggregates,
 * persisted to the event store), so they must be stable and serialisation-safe.
 */
interface DomainEvent
{
    /** Identifier of the aggregate that emitted the event. */
    public function aggregateId(): string;

    /** When the event occurred (UTC). */
    public function occurredOn(): DateTimeImmutable;

    /**
     * Stable event name, e.g. "ordering.order_placed". Used for routing and as
     * the discriminator when deserialising from the event store.
     */
    public static function eventName(): string;
}
