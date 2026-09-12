<?php

declare(strict_types=1);

namespace App\Payment\Infrastructure\Persistence\Doctrine;

use App\Payment\Domain\Model\PaymentHold;
use App\Payment\Domain\Repository\PaymentHoldRepository;
use Doctrine\ORM\EntityManagerInterface;

final readonly class DoctrinePaymentHoldRepository implements PaymentHoldRepository
{
    public function __construct(private EntityManagerInterface $em)
    {
    }

    public function add(PaymentHold $hold): void
    {
        $this->em->persist($hold);
    }

    public function existsForOrder(string $orderId): bool
    {
        return null !== $this->em->find(PaymentHold::class, $orderId);
    }

    public function removeForOrder(string $orderId): void
    {
        $hold = $this->em->find(PaymentHold::class, $orderId);
        if (null !== $hold) {
            $this->em->remove($hold);
        }
    }
}
