<?php

declare(strict_types=1);

namespace App\Shared\Domain\Aggregate;

use App\Shared\Domain\Event\DomainEvent;

/**
 * Base class for event-sourced aggregates (used by the Ordering context).
 *
 * State is never stored directly: it is derived by replaying the event stream.
 * A mutation calls {@see recordThat()}, which both buffers the event for
 * persistence AND applies it to the in-memory state via a conventional
 * `apply{EventName}()` method on the concrete aggregate.
 */
abstract class EventSourcedAggregateRoot
{
    /** @var list<DomainEvent> */
    private array $recordedEvents = [];

    private int $version = 0;

    /**
     * Rebuild an aggregate from its full (or post-snapshot) history.
     *
     * @param iterable<DomainEvent> $history
     */
    public static function reconstituteFromHistory(iterable $history): static
    {
        $aggregate = new static(); // @phpstan-ignore-line new.static

        foreach ($history as $event) {
            $aggregate->apply($event);
            ++$aggregate->version;
        }

        return $aggregate;
    }

    /** Current version == number of events applied. Used for optimistic locking. */
    public function version(): int
    {
        return $this->version;
    }

    /** @return list<DomainEvent> */
    public function pullDomainEvents(): array
    {
        $events = $this->recordedEvents;
        $this->recordedEvents = [];

        return $events;
    }

    protected function recordThat(DomainEvent $event): void
    {
        $this->recordedEvents[] = $event;
        $this->apply($event);
    }

    /**
     * Dispatch an event to its `apply{ShortClassName}` mutator.
     * Concrete aggregates implement those mutators to evolve state.
     */
    private function apply(DomainEvent $event): void
    {
        $parts = explode('\\', $event::class);
        $method = 'apply'.end($parts);

        if (method_exists($this, $method)) {
            $this->{$method}($event);
        }
    }
}
