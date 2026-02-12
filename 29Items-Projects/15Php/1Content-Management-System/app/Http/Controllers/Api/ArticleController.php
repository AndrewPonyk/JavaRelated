<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\StoreArticleRequest;
use App\Http\Resources\ArticleResource;
use App\Models\Article;
use App\Services\ContentService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * @OA\Info(
 *     title="Content Management API",
 *     version="1.0.0"
 * )
 */
class ArticleController extends Controller
{
    private ContentService $contentService;

    public function __construct(ContentService $contentService)
    {
        $this->contentService = $contentService;
    }

    /**
     * Display a listing of the resource.
     *
     * @return JsonResponse
     */
    public function index(Request $request): JsonResponse
    {
        // Example filtering pattern
        $perPage = $request->input('per_page', 15);
        $status = $request->input('status'); // Default to all if not specified (for Admin view)

        // Eloquent builder approach or Service call
        $query = Article::with('author', 'tags')
            ->orderBy('created_at', 'desc');
            
        if ($status) {
            $query->where('status', $status);
        }

        $articles = $query->paginate($perPage);

        return response()->json(
            ArticleResource::collection($articles)->response()->getData(true)
        );
    }

    /**
     * Store a newly created resource in storage.
     *
     * @param StoreArticleRequest $request
     * @return JsonResponse
     */
    public function store(StoreArticleRequest $request): JsonResponse
    {
        // Service Layer Logic: Create user, handle versions, trigger search index
        $validatedData = $request->validated();
        
        try {
            $article = $this->contentService->createArticle(
                $validatedData, 
                \App\Models\User::first() // Hardcoded user for MVP
            );

            return response()->json([
                'message' => 'Article created successfully',
                'data' => new ArticleResource($article),
            ], 201);
        } catch (\Exception $e) {
            // Log the exception
            \Log::error('Failed to create article: ' . $e->getMessage());
            
            return response()->json([
                'error' => 'Could not create article. Please try again later.'
            ], 500);
        }
    }

    /**
     * Display the specified resource.
     *
     * @param  Article  $article
     * @return JsonResponse
     */
    public function show($id): JsonResponse
    {
        // Support both ID and Slug
        $article = Article::with(['author', 'tags'])
            ->where('id', $id)
            ->orWhere('slug', $id)
            ->firstOrFail();

        return response()->json([
            'data' => new ArticleResource($article)
        ]);
    }

    /**
     * Update the specified resource in storage.
     *
     * @param  Request  $request
     * @param  Article  $article
     * @return JsonResponse
     */
    public function update(Request $request, Article $article): JsonResponse
    {
        // Validation could be a separate UpdateArticleRequest
        $validated = $request->validate([
            'title' => 'string|max:255',
            'status' => 'in:draft,published,archived',
            'content' => 'string',
        ]);

        $updatedArticle = $this->contentService->updateArticle($article, $validated);

        return response()->json([
            'message' => 'Article updated successfully',
            'data' => new ArticleResource($updatedArticle)
        ]);
    }

    /**
     * Remove the specified resource from storage.
     *
     * @param  Article  $article
     * @return JsonResponse
     */
    public function destroy(Article $article): JsonResponse
    {
        // $this->authorize('delete', $article);

        $article->delete(); // Soft delete via service ideally

        return response()->json([
            'message' => 'Article deleted successfully'
        ], 200);
    }
}
