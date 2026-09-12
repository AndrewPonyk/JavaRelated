<?php

declare(strict_types=1);

namespace App\Tests\Functional\Ordering;

use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

final class OrderApiTest extends WebTestCase
{
    public function test_placing_an_order_requires_authentication(): void
    {
        $client = self::createClient();
        $client->jsonRequest('POST', '/api/orders', ['currency' => 'USD', 'lines' => []]);

        self::assertResponseStatusCodeSame(Response::HTTP_UNAUTHORIZED);
    }

    public function test_reading_an_order_requires_authentication(): void
    {
        $client = self::createClient();
        $client->request('GET', '/api/orders/0190c2f9-0000-7000-8000-000000000001');

        self::assertResponseStatusCodeSame(Response::HTTP_UNAUTHORIZED);
    }
}
