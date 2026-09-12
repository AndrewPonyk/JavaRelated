<?php

declare(strict_types=1);

namespace App\Ordering\Infrastructure\EventStore;

use App\Ordering\Domain\Event\OrderPaid;
use App\Ordering\Domain\Event\OrderPlaced;
use App\Shared\Domain\Event\DomainEvent;
use RuntimeException;
use Symfony\Component\Serializer\Normalizer\DenormalizerInterface;
use Symfony\Component\Serializer\Normalizer\NormalizerInterface;

/**
 * Converts domain events ⇄ (eventName, json payload) for the event store.
 *
 * The name→class registry is also where **upcasters** belong: when an event
 * schema evolves, register the new version and transform old payloads on read.
 */
final readonly class EventSerializer
{
    /** @var array<string, class-string<DomainEvent>> */
    private const array REGISTRY = [
        'ordering.order_placed' => OrderPlaced::class,
        'ordering.order_paid' => OrderPaid::class,
    ];

    public function __construct(
        private NormalizerInterface&DenormalizerInterface $serializer,
    ) {
    }

    /** @return array<string, mixed> */
    public function toPayload(DomainEvent $event): array
    {
        /** @var array<string, mixed> $data */
        $data = $this->serializer->normalize($event, 'json');

        return $data;
    }

    /**
     * @param array<string, mixed> $payload
     */
    public function fromPayload(string $eventName, array $payload): DomainEvent
    {
        $class = self::REGISTRY[$eventName]
            ?? throw new RuntimeException("Unknown stored event: {$eventName}");

        // TODO: run upcasters for older payload versions before denormalising.
        return $this->serializer->denormalize($payload, $class, 'json');
    }
}
