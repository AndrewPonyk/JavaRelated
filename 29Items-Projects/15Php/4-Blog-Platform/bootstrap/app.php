<?php

declare(strict_types=1);

use App\Exceptions\PostNotPublishedException;
use App\Http\Middleware\SecurityHeaders;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/*
| Laravel 11 slim application bootstrap. Middleware, routing, and exception
| handling are configured here via the fluent builder (no more Http/Kernel.php).
*/
return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // Defensive headers (CSP, HSTS, X-Frame-Options) on every web response.
        $middleware->web(append: [
            SecurityHeaders::class,
        ]);

        // Where the auth middlewares send people (no default 'dashboard' route).
        $middleware->redirectGuestsTo(fn () => route('login'));
        $middleware->redirectUsersTo('/admin');
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        // Requesting an unpublished post must look exactly like a missing one —
        // never disclose that draft content exists (see ARCHITECTURE §2.6).
        $exceptions->render(function (PostNotPublishedException $e, Request $request): void {
            throw new NotFoundHttpException($e->getMessage(), $e);
        });
    })
    ->create();
