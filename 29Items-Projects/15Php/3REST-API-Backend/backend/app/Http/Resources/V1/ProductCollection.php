<?php

namespace App\Http\Resources\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\ResourceCollection;

/**
 * Paginated collection of products.
 *
 * We rely on Laravel's automatic pagination payload (meta + links) and only
 * augment it with the API version via paginationInformation(). Defining a
 * custom toArray() here would array_merge_recursive with the framework's
 * pagination data and corrupt scalar values into arrays.
 */
class ProductCollection extends ResourceCollection
{
    public $collects = ProductResource::class;

    /**
     * @param  array<string, mixed>  $paginated
     * @param  array<string, mixed>  $default
     * @return array<string, mixed>
     */
    public function paginationInformation(Request $request, array $paginated, array $default): array
    {
        $default['meta']['api_version'] = 'v1';

        return $default;
    }
}
