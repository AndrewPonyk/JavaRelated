<?php

declare(strict_types=1);

namespace App\Payment\Infrastructure\Persistence\Doctrine;

use App\Payment\Domain\Model\Transaction;
use App\Payment\Domain\Repository\TransactionRepository;
use Doctrine\ORM\EntityManagerInterface;

final readonly class DoctrineTransactionRepository implements TransactionRepository
{
    public function __construct(private EntityManagerInterface $em)
    {
    }

    public function add(Transaction $transaction): void
    {
        // Flush handled by the bus' doctrine_transaction middleware.
        $this->em->persist($transaction);
    }

    public function findByIdempotencyKey(string $idempotencyKey): ?Transaction
    {
        return $this->em->getRepository(Transaction::class)->findOneBy(['idempotencyKey' => $idempotencyKey]);
    }

    public function findLatestForOrder(string $orderId): ?Transaction
    {
        /** @var Transaction|null $transaction */
        $transaction = $this->em->createQuery(
            'SELECT t FROM '.Transaction::class.' t WHERE t.orderId = :oid ORDER BY t.createdAt DESC',
        )
            ->setParameter('oid', $orderId)
            ->setMaxResults(1)
            ->getOneOrNullResult();

        return $transaction;
    }
}
