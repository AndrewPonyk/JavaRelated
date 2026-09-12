<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Forces content negotiation to JSON for the API surface, so a client that
 * forgets `Accept: application/json` still gets JSON errors (not HTML),
 * keeping the error contract uniform (docs/ARCHITECTURE.md §2.6).
 */
class ForceJsonResponse
{
    public function handle(Request $request, Closure $next): Response
    {
        $request->headers->set('Accept', 'application/json');

        return $next($request);
    }
}
