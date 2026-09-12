<?php

declare(strict_types=1);

namespace App\Models;

use App\Observers\PostObserver;
use Database\Factories\PostFactory;
use Illuminate\Database\Eloquent\Attributes\ObservedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Support\Carbon;
use Illuminate\Support\Str;

/**
 * @property int $id
 * @property string $title
 * @property string $slug
 * @property string|null $excerpt
 * @property string $body Raw Markdown source.
 * @property string|null $body_html Sanitised, render-on-save HTML.
 * @property int $reading_time Minutes, computed on save.
 * @property string $status draft|published
 * @property Carbon|null $published_at
 * @property int|null $category_id
 * @property int $user_id
 */
#[ObservedBy(PostObserver::class)]
class Post extends Model
{
    /** @use HasFactory<PostFactory> */
    use HasFactory;

    public const STATUS_DRAFT = 'draft';

    public const STATUS_PUBLISHED = 'published';

    /** @var list<string> */
    protected $fillable = [
        'title',
        'slug',
        'excerpt',
        'body',
        'body_html',
        'reading_time',
        'status',
        'published_at',
        'category_id',
        'user_id',
        // SEO overrides (fall back to derived values when null).
        'meta_title',
        'meta_description',
        'og_image',
    ];

    /**
     * Use the slug for implicit route-model binding (SEO-friendly URLs).
     */
    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'published_at' => 'datetime',
            'reading_time' => 'integer',
        ];
    }

    // ──────────────────────────────────────────────────────────────────────
    // Relationships
    // ──────────────────────────────────────────────────────────────────────

    /** @return BelongsTo<User, $this> */
    public function author(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    /** @return BelongsTo<Category, $this> */
    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    /** @return BelongsToMany<Tag, $this> */
    public function tags(): BelongsToMany
    {
        return $this->belongsToMany(Tag::class)->withTimestamps();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scopes
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Only posts that are published AND whose publish date has passed.
     *
     * @param  Builder<Post>  $query
     */
    public function scopePublished(Builder $query): void
    {
        $query->where('status', self::STATUS_PUBLISHED)
            ->where('published_at', '<=', now());
    }

    /**
     * Case-insensitive search over title/body using the SQLite `LIKE` path.
     * Sufficient for a personal blog's corpus; see docs/PROJECT-PLAN.md Phase 3
     * for the SQLite FTS5 / Laravel Scout upgrade path.
     *
     * @param  Builder<Post>  $query
     */
    public function scopeSearch(Builder $query, ?string $term): void
    {
        if (blank($term)) {
            return;
        }

        $query->where(function (Builder $q) use ($term): void {
            $q->where('title', 'like', "%{$term}%")
                ->orWhere('body', 'like', "%{$term}%");
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    public function isPublished(): bool
    {
        return $this->status === self::STATUS_PUBLISHED
            && $this->published_at !== null
            && $this->published_at->isPast();
    }

    /**
     * Derive a meta description from the explicit excerpt or the post body.
     */
    public function metaDescription(): string
    {
        $source = $this->meta_description
            ?? $this->excerpt
            ?? strip_tags((string) $this->body_html);

        return Str::limit(trim($source), (int) config('blog.excerpt_length', 160));
    }
}
