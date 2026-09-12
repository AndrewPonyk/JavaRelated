<?php

declare(strict_types=1);

namespace App\Search\Infrastructure\Elasticsearch;

use Elastic\Elasticsearch\Client;
use Elastic\Elasticsearch\ClientBuilder;

/** Builds the Elasticsearch client (factory referenced from elasticsearch.yaml). */
final class ClientFactory
{
    public static function create(string $url): Client
    {
        return ClientBuilder::create()
            ->setHosts([$url])
            ->setRetries(2)
            ->build();
    }
}
