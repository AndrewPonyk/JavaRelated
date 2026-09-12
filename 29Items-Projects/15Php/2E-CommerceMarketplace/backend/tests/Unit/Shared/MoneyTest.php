<?php

declare(strict_types=1);

namespace App\Tests\Unit\Shared;

use App\Shared\Domain\ValueObject\Money;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Test;
use PHPUnit\Framework\TestCase;

final class MoneyTest extends TestCase
{
    #[Test]
    public function it_adds_amounts_of_the_same_currency(): void
    {
        $sum = Money::of(1500, 'USD')->add(Money::of(250, 'USD'));

        self::assertSame(1750, $sum->amountMinor);
        self::assertSame('USD', $sum->currency);
    }

    #[Test]
    public function it_rejects_mixed_currency_arithmetic(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Money::of(100, 'USD')->add(Money::of(100, 'EUR'));
    }

    #[Test]
    public function it_computes_commission_in_basis_points_rounding_half_up(): void
    {
        // 10% of 1999 minor units = 199.9 → 200.
        self::assertSame(200, Money::of(1999, 'USD')->percentageBps(1000)->amountMinor);
    }
}
