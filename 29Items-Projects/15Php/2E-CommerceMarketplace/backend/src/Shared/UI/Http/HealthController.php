<?php

declare(strict_types=1);

namespace App\Shared\UI\Http;

use Doctrine\DBAL\Connection;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Throwable;

/**
 * Liveness & readiness probes for Azure Container Apps.
 * - /health/live  : process is up (no dependencies checked).
 * - /health/ready : dependencies (DB) reachable — gates traffic on rollout.
 */
final class HealthController extends AbstractController
{
    #[Route('/health/live', name: 'health_live', methods: ['GET'])]
    public function live(): JsonResponse
    {
        return new JsonResponse(['status' => 'ok']);
    }

    #[Route('/health/ready', name: 'health_ready', methods: ['GET'])]
    public function ready(Connection $db): JsonResponse
    {
        try {
            $db->executeQuery('SELECT 1');
        } catch (Throwable) {
            return new JsonResponse(['status' => 'degraded', 'db' => 'unreachable'], Response::HTTP_SERVICE_UNAVAILABLE);
        }

        return new JsonResponse(['status' => 'ok', 'db' => 'ok']);
    }
}
