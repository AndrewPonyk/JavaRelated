<?php

declare(strict_types=1);

namespace App\Ordering\Infrastructure\EventStore;

use App\Ordering\Domain\Model\Order;
use App\Ordering\Domain\Model\OrderId;
use App\Ordering\Domain\Model\OrderNotFoundException;
use App\Ordering\Domain\Repository\ConcurrencyException;
use App\Ordering\Domain\Repository\OrderEventStoreInterface;
use App\Shared\Domain\Bus\Event\EventBus;
use Doctrine\DBAL\Exception\UniqueConstraintViolationException;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Doctrine-backed event store for the Order aggregate.
 *
 * `save()` appends newly-recorded events with monotonically increasing versions;
 * the unique (aggregate_id, version) constraint turns a concurrent append into a
 * {@see ConcurrencyException}. After a successful append the events are published
 * on the event bus (transactional-outbox semantics — async subscribers only ever
 * see committed facts; see docs/TECH-NOTES.md).
 *
 * TODO Phase 3: read from / write snapshots every N events to bound replay cost.
 */
final readonly class DoctrineOrderEventStore implements OrderEventStoreInterface
{
    public function __construct(
        private EntityManagerInterface $em,
        private EventSerializer $serializer,
        private EventBus $eventBus,
    ) {
    }

    public function save(Order $order): void
    {
        $events = $order->pullDomainEvents();
        if ([] === $events) {
            return;
        }

        $version = $order->version();
        foreach ($events as $event) {
            $this->em->persist(new StoredEvent(
                aggregateId: $event->aggregateId(),
                version: ++$version,
                eventName: $event::eventName(),
                payload: $this->serializer->toPayload($event),
                occurredOn: $event->occurredOn(),
            ));
        }

        try {
            $this->em->flush();
        } catch (UniqueConstraintViolationException) {
            throw ConcurrencyException::streamChanged((string) $order->id(), $order->version(), $version);
        }

        $this->eventBus->publish(...$events);
    }

    public function load(OrderId $id): Order
    {
        /** @var list<StoredEvent> $rows */
        $rows = $this->em->createQuery(
            'SELECT e FROM '.StoredEvent::class.' e WHERE e.aggregateId = :id ORDER BY e.version ASC',
        )->setParameter('id', (string) $id)->getResult();

        if ([] === $rows) {
            throw OrderNotFoundException::withId($id);
        }

        $history = array_map(
            fn (StoredEvent $row) => $this->serializer->fromPayload($row->eventName, $row->payload),
            $rows,
        );

        return Order::reconstituteFromHistory($history);
    }
}
