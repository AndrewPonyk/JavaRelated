<?php

declare(strict_types=1);

namespace App\Tests\Functional\Catalog;

use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

/**
 * Functional test through the HTTP kernel. Verifies the security layer rejects
 * anonymous writes before the controller/domain is reached. The authenticated
 * create → read happy path (which needs PostgreSQL) lives in the Integration
 * suite: {@see \App\Tests\Integration\Catalog\ProductApiFlowTest}.
 */
final class ProductApiTest extends WebTestCase
{
    public function test_anonymous_user_cannot_create_a_product(): void
    {
        $client = self::createClient();

        $client->jsonRequest('POST', '/api/products', [
            'name' => 'Test',
            'priceMinor' => 100,
            'currency' => 'USD',
            'stock' => 1,
        ]);

        self::assertResponseStatusCodeSame(Response::HTTP_UNAUTHORIZED);
    }

    public function test_health_liveness_is_public(): void
    {
        $client = self::createClient();
        $client->request('GET', '/health/live');

        self::assertResponseIsSuccessful();
    }
}
