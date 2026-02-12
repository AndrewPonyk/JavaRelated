<?php

namespace App\Models;

use App\Models\Category;
use App\Models\Tag;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\SoftDeletes;

class Article extends Model
{
    use HasFactory, SoftDeletes;

    protected $fillable = [
        'title',
        'slug',
        'excerpt',
        'content',
        'status',
        'version',
        'meta',
        'published_at',
        'author_id',
        'category_id',
        'parent_id',
    ];

    /**
     * The attributes that should be cast.
     *
     * @var array
     */
    protected $casts = [
        'meta' => 'array',
        'published_at' => 'datetime',
        'version' => 'integer',
    ];

    /**
     * Get the author that owns the article.
     */
    public function author(): BelongsTo
    {
        return $this->belongsTo(User::class, 'author_id');
    }

    /**
     * Get the category that the article belongs to.
     */
    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    /**
     * Get the tags associated with the article.
     */
    public function tags(): BelongsToMany
    {
        return $this->belongsToMany(Tag::class);
    }

    /**
     * Get the revisions for the article.
     * Assuming a separate ArticleRevision model exists or self-referential
     * For now, returning dummy HasMany or self-relation logic if revisions exist.
     * Often complex CMS uses a separate table like article_revisions.
     */
    public function revisions()
    {
        // Placeholder implementation until ArticleRevision model is created
        // return $this->hasMany(ArticleRevision::class);
        return $this->hasMany(Article::class, 'parent_id'); // Example self-ref for simplistic versioning
    }
}
