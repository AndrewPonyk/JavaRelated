<?php

declare(strict_types=1);

namespace App\Shared\Domain\ValueObject;

use InvalidArgumentException;

/**
 * Money value object — integer minor units (e.g. cents) + ISO-4217 currency.
 *
 * Never represent money as a float. Arithmetic stays in integer minor units to
 * avoid rounding errors; commission is computed in basis points.
 */
final readonly class Money
{
    public function __construct(
        public int $amountMinor,
        public string $currency,
    ) {
        if ('' === $currency || 3 !== \strlen($currency)) {
            throw new InvalidArgumentException('Currency must be a 3-letter ISO-4217 code.');
        }
    }

    public static function of(int $amountMinor, string $currency): self
    {
        return new self($amountMinor, strtoupper($currency));
    }

    public static function zero(string $currency): self
    {
        return new self(0, strtoupper($currency));
    }

    public function add(self $other): self
    {
        $this->assertSameCurrency($other);

        return new self($this->amountMinor + $other->amountMinor, $this->currency);
    }

    public function multiply(int $factor): self
    {
        return new self($this->amountMinor * $factor, $this->currency);
    }

    /**
     * Apply a commission expressed in basis points (1% = 100 bps), rounding
     * half-up to the nearest minor unit.
     */
    public function percentageBps(int $bps): self
    {
        $amount = (int) round(($this->amountMinor * $bps) / 10_000);

        return new self($amount, $this->currency);
    }

    public function equals(self $other): bool
    {
        return $this->amountMinor === $other->amountMinor
            && $this->currency === $other->currency;
    }

    private function assertSameCurrency(self $other): void
    {
        if ($this->currency !== $other->currency) {
            throw new InvalidArgumentException(\sprintf('Currency mismatch: %s vs %s.', $this->currency, $other->currency));
        }
    }
}
