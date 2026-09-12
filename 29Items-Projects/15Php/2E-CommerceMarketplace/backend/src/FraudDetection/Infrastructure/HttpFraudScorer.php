<?php

declare(strict_types=1);

namespace App\FraudDetection\Infrastructure;

use App\FraudDetection\Application\FraudScorer;
use App\FraudDetection\Domain\Model\RiskScore;
use Psr\Log\LoggerInterface;
use RuntimeException;
use Symfony\Component\HttpClient\Exception\TransportException;
use Symfony\Contracts\HttpClient\HttpClientInterface;
use Throwable;

/**
 * Anti-corruption layer: adapts the external Python ML micro-service to the
 * {@see FraudScorer} port. Calls POST {fraudServiceBaseUrl}/score with a tight
 * timeout. On ANY failure (timeout, 5xx, malformed body) it **fails open** to a
 * mid risk score that maps to manual REVIEW — never blocking nor silently
 * allowing on an ML outage.
 *
 * TODO Phase 3: wrap in a circuit breaker so repeated failures short-circuit
 *       straight to the fallback without hammering a sick service.
 */
final readonly class HttpFraudScorer implements FraudScorer
{
    private const float FAIL_OPEN_SCORE = 0.6; // → REVIEW

    public function __construct(
        private HttpClientInterface $httpClient,
        private LoggerInterface $logger,
        private string $fraudServiceBaseUrl,
        private int $timeoutMs,
    ) {
    }

    public function score(array $features): RiskScore
    {
        try {
            $response = $this->httpClient->request('POST', rtrim($this->fraudServiceBaseUrl, '/').'/score', [
                'json' => ['features' => $features],
                'timeout' => $this->timeoutMs / 1000,
            ]);

            // Untrusted external payload — type is genuinely mixed until validated.
            /** @var array<string, mixed> $data */
            $data = $response->toArray();
            $risk = $data['risk'] ?? null;

            if (!\is_float($risk) && !\is_int($risk)) {
                throw new RuntimeException('Fraud service returned no "risk" field.');
            }

            return new RiskScore((float) $risk);
        } catch (TransportException|Throwable $e) {
            $this->logger->warning('Fraud service unavailable — failing open to REVIEW.', [
                'error' => $e->getMessage(),
            ]);

            return new RiskScore(self::FAIL_OPEN_SCORE);
        }
    }
}
