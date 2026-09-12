<?php

declare(strict_types=1);

namespace App\Tests\Integration\Catalog;

use App\Identity\Domain\Model\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Uid\Uuid;

/**
 * End-to-end API happy path through the HTTP kernel + real PostgreSQL (CI).
 * Authenticates exactly like a real client of the stateless API — obtains a JWT
 * from /api/auth/login and sends it as a bearer token — then creates a product,
 * reads it back, and updates its price.
 */
final class ProductApiFlowTest extends WebTestCase
{
    public function test_a_seller_creates_reads_and_updates_a_product(): void
    {
        $client = static::createClient();
        $container = static::getContainer();
        $em = $container->get(EntityManagerInterface::class);
        $hasher = $container->get(UserPasswordHasherInterface::class);

        $seller = User::register(Uuid::v7()->toRfc4122(), 'flow-seller@example.com', [User::ROLE_SELLER]);
        $seller->setHashedPassword($hasher->hashPassword($seller, 'sup3r-secret-pw'));
        $em->persist($seller);
        $em->flush();

        // Authenticate via the real JWT flow; reuse the bearer token per request.
        $client->jsonRequest('POST', '/api/auth/login', [
            'email' => 'flow-seller@example.com',
            'password' => 'sup3r-secret-pw',
        ]);
        self::assertResponseIsSuccessful();
        $token = self::decodeJsonBody($client)['token'];
        self::assertIsString($token);
        $auth = ['HTTP_AUTHORIZATION' => 'Bearer '.$token];

        // Create
        $client->jsonRequest('POST', '/api/products', [
            'name' => 'Gizmo',
            'description' => 'A fine gizmo',
            'priceMinor' => 1500,
            'currency' => 'USD',
            'stock' => 4,
        ], $auth);
        self::assertResponseStatusCodeSame(Response::HTTP_CREATED);
        $created = self::decodeJsonBody($client);
        $id = $created['id'];
        self::assertIsString($id);

        // Read (public — no auth required)
        $client->request('GET', '/api/products/'.$id);
        self::assertResponseIsSuccessful();
        $body = self::decodeJsonBody($client);
        self::assertSame('Gizmo', $body['name']);
        self::assertIsArray($body['price']);
        self::assertSame(1500, $body['price']['amountMinor']);

        // Update price
        $client->jsonRequest('PATCH', '/api/products/'.$id, ['priceMinor' => 1999], $auth);
        self::assertResponseIsSuccessful();
        $updated = self::decodeJsonBody($client);
        self::assertIsArray($updated['price']);
        self::assertSame(1999, $updated['price']['amountMinor']);
    }

    /**
     * Decode the last JSON response body as an array, asserting the response and
     * payload shape so the assertions below stay statically typed (PHPStan L9).
     *
     * @return array<array-key, mixed>
     */
    private static function decodeJsonBody(KernelBrowser $client): array
    {
        $response = $client->getResponse();
        self::assertInstanceOf(Response::class, $response);

        $decoded = json_decode((string) $response->getContent(), true);
        self::assertIsArray($decoded);

        return $decoded;
    }
}
