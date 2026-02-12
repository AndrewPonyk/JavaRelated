<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class ArticleResource extends JsonResource
{
    /**
     * Transform the resource into an array.
     *
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'title' => $this->title,
            'slug' => $this->slug,
            'content' => $this->when($request->routeIs('*.show'), $this->content, str($this->content)->limit(200)), // Summary for list view
            'excerpt' => $this->excerpt,
            'author' => new UserResource($this->whenLoaded('author')),
            'status' => $this->status,
            'tags' => TagResource::collection($this->whenLoaded('tags')),
            'meta' => $this->meta ?? [],
            'published_at' => $this->published_at,
            'created_at' => $this->created_at,
            'updated_at' => $this->updated_at,
        ];
    }
}
