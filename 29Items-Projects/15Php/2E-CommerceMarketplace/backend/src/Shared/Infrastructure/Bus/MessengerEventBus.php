<?php

declare(strict_types=1);

namespace App\Shared\Infrastructure\Bus;

use App\Shared\Domain\Bus\Event\EventBus;
use App\Shared\Domain\Event\DomainEvent;
use Symfony\Component\Messenger\MessageBusInterface;

/**
 * Publishes domain events on Messenger's event.bus. Events routed to the `async`
 * transport (see messenger.yaml) are delivered to RabbitMQ for cross-context
 * consumers (search indexing, fraud scoring, commission accrual, notifications).
 */
final readonly class MessengerEventBus implements EventBus
{
    public function __construct(private MessageBusInterface $eventBus)
    {
    }

    public function publish(DomainEvent ...$events): void
    {
        foreach ($events as $event) {
            $this->eventBus->dispatch($event);
        }
    }
}
