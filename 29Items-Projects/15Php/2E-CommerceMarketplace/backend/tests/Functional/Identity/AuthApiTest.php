<?php

declare(strict_types=1);

namespace App\Tests\Functional\Identity;

use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

/**
 * Boundary tests that exercise validation and the security layer without touching
 * infrastructure (these reject the request before any handler/DB access).
 */
final class AuthApiTest extends WebTestCase
{
    public function test_registration_rejects_an_invalid_email(): void
    {
        $client = self::createClient();

        $client->jsonRequest('POST', '/api/auth/register', [
            'email' => 'not-an-email',
            'password' => 'supersecret',
            'accountType' => 'customer',
        ]);

        self::assertResponseStatusCodeSame(Response::HTTP_UNPROCESSABLE_ENTITY);
    }

    public function test_registration_rejects_a_short_password(): void
    {
        $client = self::createClient();

        $client->jsonRequest('POST', '/api/auth/register', [
            'email' => 'new@example.com',
            'password' => 'short',
        ]);

        self::assertResponseStatusCodeSame(Response::HTTP_UNPROCESSABLE_ENTITY);
    }

    public function test_me_requires_authentication(): void
    {
        $client = self::createClient();
        $client->request('GET', '/api/auth/me');

        self::assertResponseStatusCodeSame(Response::HTTP_UNAUTHORIZED);
    }
}
