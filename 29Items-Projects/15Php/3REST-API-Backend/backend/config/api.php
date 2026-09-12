<?php

return [

    /*
    |--------------------------------------------------------------------------
    | API Versioning & Rate Limiting
    |--------------------------------------------------------------------------
    | Values are environment-driven so limits can be tuned per environment
    | without a code change (see docs/TECH-NOTES.md §3.4).
    */

    'default_version' => env('API_DEFAULT_VERSION', 'v1'),

    // Requests/minute for an authenticated user on the general API.
    'rate_limit_per_minute' => (int) env('API_RATE_LIMIT_PER_MINUTE', 60),

    // Requests/minute for credential endpoints (login/register) per IP.
    'auth_rate_limit' => (int) env('API_AUTH_RATE_LIMIT', 5),

    /*
    |--------------------------------------------------------------------------
    | Recommendation engine
    |--------------------------------------------------------------------------
    */
    'recommendation' => [
        'clusters' => (int) env('RECOMMENDATION_CLUSTERS', 8),  // k for k-means
        'top_n' => (int) env('RECOMMENDATION_TOP_N', 10),    // recs per user
    ],

];
