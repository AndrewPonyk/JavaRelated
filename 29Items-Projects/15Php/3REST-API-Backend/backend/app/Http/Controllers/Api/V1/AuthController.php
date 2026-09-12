<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Requests\V1\LoginRequest;
use App\Http\Requests\V1\RegisterRequest;
use App\Http\Resources\V1\UserResource;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;
use OpenApi\Attributes as OA;

/**
 * Token-based auth via Laravel Sanctum.
 * These routes are protected by the strict `throttle:auth` limiter
 * (see routes/api_v1.php) to blunt credential-stuffing.
 */
class AuthController extends Controller
{
    #[OA\Post(
        path: '/auth/register',
        summary: 'Register a new account and return an access token',
        tags: ['Auth'],
        responses: [new OA\Response(response: 201, description: 'Registered + token issued')],
    )]
    public function register(RegisterRequest $request): JsonResponse
    {
        $data = $request->validated();

        $user = User::create([
            'name' => $data['name'],
            'email' => $data['email'],
            'password' => $data['password'], // hashed via the model cast
            'role' => 'customer',
        ]);

        return response()->json([
            'user' => new UserResource($user),
            'token' => $user->createToken('api')->plainTextToken,
        ], 201);
    }

    #[OA\Post(
        path: '/auth/login',
        summary: 'Exchange credentials for an access token',
        tags: ['Auth'],
        responses: [
            new OA\Response(response: 200, description: 'Token issued'),
            new OA\Response(response: 422, description: 'Invalid credentials'),
        ],
    )]
    public function login(LoginRequest $request): JsonResponse
    {
        $credentials = $request->validated();

        $user = User::where('email', $credentials['email'])->first();

        if (! $user || ! Hash::check($credentials['password'], $user->password)) {
            // Generic message — never reveal whether the email exists.
            throw ValidationException::withMessages([
                'email' => ['The provided credentials are incorrect.'],
            ]);
        }

        return response()->json([
            'user' => new UserResource($user),
            'token' => $user->createToken('api')->plainTextToken,
        ]);
    }

    #[OA\Get(
        path: '/auth/me',
        summary: 'Current authenticated user',
        security: [['sanctum' => []]],
        tags: ['Auth'],
        responses: [new OA\Response(response: 200, description: 'The authenticated user')],
    )]
    public function me(Request $request): JsonResponse
    {
        return response()->json(['user' => new UserResource($request->user())]);
    }

    #[OA\Post(
        path: '/auth/logout',
        summary: 'Revoke the current access token',
        security: [['sanctum' => []]],
        tags: ['Auth'],
        responses: [new OA\Response(response: 200, description: 'Token revoked')],
    )]
    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()->delete();

        return response()->json(['message' => 'Logged out.']);
    }
}
