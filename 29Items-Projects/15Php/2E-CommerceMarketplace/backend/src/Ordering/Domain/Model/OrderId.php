<?php

declare(strict_types=1);

namespace App\Ordering\Domain\Model;

use InvalidArgumentException;
use Stringable;
use Symfony\Component\Uid\Uuid;

final readonly class OrderId implements Stringable
{
    public function __construct(public string $value)
    {
        if (!Uuid::isValid($value)) {
            throw new InvalidArgumentException('Invalid OrderId: '.$value);
        }
    }

    public static function generate(): self
    {
        return new self(Uuid::v7()->toRfc4122());
    }

    public static function fromString(string $value): self
    {
        return new self($value);
    }

    public function __toString(): string
    {
        return $this->value;
    }
}
