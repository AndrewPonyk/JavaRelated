<?php

namespace App\Http\Controllers\Api\V1;

use Illuminate\Foundation\Auth\Access\AuthorizesRequests;
use Illuminate\Foundation\Validation\ValidatesRequests;
use Illuminate\Routing\Controller as BaseController;
use OpenApi\Attributes as OA;

/*
|--------------------------------------------------------------------------
| API v1 Base Controller
|--------------------------------------------------------------------------
| Hosts the root OpenAPI document metadata. L5-Swagger scans these
| attributes to generate /api/documentation.
*/

#[OA\Info(
    version: '1.0.0',
    title: 'Marketplace REST API',
    description: 'Marketplace/SaaS backend — auth, product CRUD, event-driven notifications, and behavior-based recommendations.',
    contact: new OA\Contact(email: 'api@example.com'),
)]
#[OA\Server(url: '/api/v1', description: 'API v1')]
#[OA\SecurityScheme(
    securityScheme: 'sanctum',
    type: 'http',
    scheme: 'bearer',
    description: 'Sanctum personal access token. Send as: Authorization: Bearer {token}',
)]
#[OA\Tag(name: 'Auth', description: 'Registration, login, and token lifecycle')]
#[OA\Tag(name: 'Products', description: 'Marketplace product catalog (CRUD)')]
#[OA\Tag(name: 'Orders', description: 'Checkout and order history')]
#[OA\Tag(name: 'Events', description: 'Behavioral telemetry (views, wishlists, add-to-cart)')]
#[OA\Tag(name: 'Recommendations', description: 'Personalized, behavior-based recommendations')]
abstract class Controller extends BaseController
{
    use AuthorizesRequests, ValidatesRequests;
}
