<?php

declare(strict_types=1);

namespace App\Tests\Support;

use App\Vendor\Domain\Model\CommissionLedgerEntry;
use App\Vendor\Domain\Repository\CommissionLedgerRepository;

final class InMemoryCommissionLedgerRepository implements CommissionLedgerRepository
{
    /** @var list<CommissionLedgerEntry> */
    public array $entries = [];

    public function add(CommissionLedgerEntry $entry): void
    {
        $this->entries[] = $entry;
    }

    public function hasEntryForOrder(string $orderId): bool
    {
        foreach ($this->entries as $entry) {
            if ($entry->orderId() === $orderId) {
                return true;
            }
        }

        return false;
    }

    public function summaryForSeller(string $sellerId): array
    {
        $gross = 0;
        $commission = 0;
        $count = 0;
        $currency = 'USD';
        foreach ($this->entries as $entry) {
            if ($entry->sellerId() !== $sellerId) {
                continue;
            }
            $gross += $entry->grossMinor();
            $commission += $entry->commissionMinor();
            $currency = $entry->currency();
            ++$count;
        }

        return ['currency' => $currency, 'grossMinor' => $gross, 'commissionMinor' => $commission, 'entries' => $count];
    }

    public function recentForSeller(string $sellerId, int $limit = 50): array
    {
        return array_values(array_filter(
            $this->entries,
            static fn (CommissionLedgerEntry $e): bool => $e->sellerId() === $sellerId,
        ));
    }
}
