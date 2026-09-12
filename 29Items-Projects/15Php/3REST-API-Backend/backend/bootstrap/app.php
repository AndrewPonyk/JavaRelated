<?php

use App\Exceptions\InsufficientStockException;
use App\Http\Middleware\ForceJsonResponse;
use Illuminate\Auth\Access\AuthorizationException;
use Illuminate\Auth\AuthenticationException;
use Illuminate\Database\Eloquent\ModelNotFoundException;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Exceptions\HttpResponseException;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/*
|--------------------------------------------------------------------------
| Application Bootstrap (Laravel 11 style)
|--------------------------------------------------------------------------
| Middleware, routing, and the global exception → JSON mapping all live
| here. Keeping the error contract centralized means every API failure
| returns the same envelope (see docs/ARCHITECTURE.md §2.6).
*/

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up', // load-balancer health probe
    )
    ->withMiddleware(function (Middleware $middleware) {
        // Every /api/* request negotiates JSON, even when the client forgets the Accept header.
        $middleware->api(prepend: [
            ForceJsonResponse::class,
        ]);

        // Sanctum stateful guard for first-party SPA requests.
        $middleware->statefulApi();
    })
    ->withExceptions(function (Exceptions $exceptions) {
        // Single source of truth for the JSON error envelope.
        $exceptions->render(function (Throwable $e, Request $request) {
            if (! $request->is('api/*')) {
                return null; // fall back to default rendering for non-API routes
            }

            // HttpResponseException carries its own response (e.g. the throttle
            // limiter's custom 429). Let Laravel deliver it untouched.
            if ($e instanceof HttpResponseException) {
                return null;
            }

            [$status, $code] = match (true) {
                $e instanceof ValidationException => [422, 'VALIDATION_FAILED'],
                $e instanceof AuthenticationException => [401, 'UNAUTHENTICATED'],
                $e instanceof AuthorizationException => [403, 'FORBIDDEN'],
                $e instanceof InsufficientStockException => [409, 'INSUFFICIENT_STOCK'],
                $e instanceof ModelNotFoundException => [404, 'RESOURCE_NOT_FOUND'],
                $e instanceof NotFoundHttpException => [404, 'RESOURCE_NOT_FOUND'],
                $e instanceof HttpExceptionInterface => [$e->getStatusCode(), 'HTTP_ERROR'],
                default => [500, 'SERVER_ERROR'],
            };

            $payload = [
                'message' => $status === 500 && ! config('app.debug')
                    ? 'Server Error'
                    : $e->getMessage(),
                'error_code' => $code,
                'request_id' => $request->headers->get('X-Request-Id', (string) Str::uuid()),
            ];

            if ($e instanceof ValidationException) {
                $payload['errors'] = $e->errors();
            }

            // Debug-only diagnostics (never exposed in production responses).
            if (config('app.debug') && $status === 500) {
                $payload['debug'] = [
                    'exception' => $e::class,
                    'at' => $e->getFile().':'.$e->getLine(),
                ];
            }

            // Production: ship 5xx to Sentry here before returning.
            return response()->json($payload, $status);
        });
    })->create();
