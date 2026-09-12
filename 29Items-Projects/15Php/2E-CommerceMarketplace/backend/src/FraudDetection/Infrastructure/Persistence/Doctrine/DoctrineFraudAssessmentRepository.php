<?php

declare(strict_types=1);

namespace App\FraudDetection\Infrastructure\Persistence\Doctrine;

use App\FraudDetection\Domain\Model\FraudAssessment;
use App\FraudDetection\Domain\Repository\FraudAssessmentRepository;
use Doctrine\ORM\EntityManagerInterface;

final readonly class DoctrineFraudAssessmentRepository implements FraudAssessmentRepository
{
    public function __construct(private EntityManagerInterface $em)
    {
    }

    public function add(FraudAssessment $assessment): void
    {
        $this->em->persist($assessment);
    }

    public function findByOrderId(string $orderId): ?FraudAssessment
    {
        return $this->em->getRepository(FraudAssessment::class)->findOneBy(['orderId' => $orderId]);
    }

    public function listPending(int $limit = 50): array
    {
        /** @var list<FraudAssessment> $result */
        $result = $this->em->createQuery(
            'SELECT a FROM '.FraudAssessment::class.' a WHERE a.status = :pending ORDER BY a.createdAt DESC',
        )
            ->setParameter('pending', FraudAssessment::STATUS_PENDING)
            ->setMaxResults($limit)
            ->getResult();

        return $result;
    }
}
