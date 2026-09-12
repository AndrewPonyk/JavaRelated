<?php

declare(strict_types=1);

namespace App\Tests\Support;

use App\Shared\Domain\Bus\Event\EventBus;
use App\Shared\Domain\Event\DomainEvent;

/** Test double that records published domain events for assertions. */
final class RecordingEventBus implements EventBus
{
    /** @var list<DomainEvent> */
    public array $events = [];

    public function publish(DomainEvent ...$events): void
    {
        foreach ($events as $event) {
            $this->events[] = $event;
        }
    }

    /**
     * @template T of DomainEvent
     *
     * @param class-string<T> $class
     *
     * @return list<T>
     */
    public function ofType(string $class): array
    {
        return array_values(array_filter($this->events, static fn (DomainEvent $e): bool => $e instanceof $class));
    }
}
