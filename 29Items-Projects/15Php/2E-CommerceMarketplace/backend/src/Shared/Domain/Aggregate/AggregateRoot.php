<?php

declare(strict_types=1);

namespace App\Shared\Domain\Aggregate;

use App\Shared\Domain\Event\DomainEvent;

/**
 * Base class for aggregate roots.
 *
 * Aggregates record domain events as they mutate state; the application layer
 * pulls them after a successful transaction and publishes them on the event bus
 * (transactional-outbox semantics). Event-sourced aggregates additionally
 * reconstitute their state by folding their past events (see Ordering\Order).
 */
abstract class AggregateRoot
{
    /** @var list<DomainEvent> */
    private array $recordedEvents = [];

    /**
     * Pull and clear the events recorded since the last pull.
     *
     * @return list<DomainEvent>
     */
    public function pullDomainEvents(): array
    {
        $events = $this->recordedEvents;
        $this->recordedEvents = [];

        return $events;
    }

    protected function recordThat(DomainEvent $event): void
    {
        $this->recordedEvents[] = $event;
    }
}
