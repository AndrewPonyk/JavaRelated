<?php

declare(strict_types=1);

namespace App\Services;

use App\Models\Post;
use Illuminate\Contracts\Cache\Repository as Cache;
use Illuminate\Support\Collection;

/**
 * Recommends related posts using TF-IDF vectorisation + cosine similarity.
 *
 * Algorithm
 * ---------
 *   1. Build a document (string) per post from its title + plain-text body
 *      (title is weighted by repetition since it's a strong topical signal).
 *   2. Tokenise → lowercase → strip stopwords/short tokens.
 *   3. Term Frequency (TF): normalised count of each term within a document.
 *   4. Inverse Document Frequency (IDF): log(N / df) — rare terms weigh more.
 *   5. TF-IDF vector per document = TF * IDF.
 *   6. Similarity(target, doc) = cosine(target_vec, doc_vec) ∈ [0, 1].
 *   7. Return the top-N most similar published posts.
 *
 * Performance: the whole corpus is vectorised in memory (fine for a personal
 * blog). Results are cached per-post; invalidate on publish/update. For large
 * corpora, precompute in a queued job (see docs/TECH-NOTES.md §3.6).
 */
class RelatedPostsService
{
    /**
     * A compact English stopword list — common words carry no topical signal
     * and would otherwise dominate the vectors.
     *
     * @var array<string, true>
     */
    private const STOPWORDS = [
        'the' => true, 'a' => true, 'an' => true, 'and' => true, 'or' => true,
        'but' => true, 'if' => true, 'then' => true, 'is' => true, 'are' => true,
        'was' => true, 'were' => true, 'be' => true, 'been' => true, 'being' => true,
        'to' => true, 'of' => true, 'in' => true, 'on' => true, 'at' => true,
        'by' => true, 'for' => true, 'with' => true, 'about' => true, 'as' => true,
        'into' => true, 'this' => true, 'that' => true, 'these' => true, 'those' => true,
        'it' => true, 'its' => true, 'they' => true, 'them' => true, 'their' => true,
        'you' => true, 'your' => true, 'we' => true, 'our' => true, 'i' => true,
        'he' => true, 'she' => true, 'his' => true, 'her' => true, 'from' => true,
        'not' => true, 'no' => true, 'so' => true, 'than' => true, 'too' => true,
        'can' => true, 'will' => true, 'just' => true, 'do' => true, 'does' => true,
        'have' => true, 'has' => true, 'had' => true, 'how' => true, 'what' => true,
        'which' => true, 'who' => true, 'when' => true, 'where' => true, 'why' => true,
    ];

    public function __construct(
        private readonly MarkdownService $markdown,
        private readonly Cache $cache,
    ) {}

    /**
     * Return up to $limit posts most similar to the given post.
     *
     * @return Collection<int, Post>
     */
    public function relatedTo(Post $post, ?int $limit = null): Collection
    {
        $limit ??= (int) config('blog.related_posts_count', 3);
        $ttl = (int) config('blog.related_cache_ttl', 86400);

        /** @var array<int, float> $scoredIds  postId => similarity */
        $scoredIds = $this->cache->remember(
            $this->cacheKey($post->id, $limit),
            $ttl,
            fn (): array => $this->computeRelatedIds($post, $limit),
        );

        if ($scoredIds === []) {
            return collect();
        }

        // Re-fetch models and preserve the similarity ordering.
        $posts = Post::query()
            ->published()
            ->whereIn('id', array_keys($scoredIds))
            ->get()
            ->keyBy('id');

        return collect(array_keys($scoredIds))
            ->map(fn (int $id) => $posts->get($id))
            ->filter()
            ->values();
    }

    /**
     * Heavy lifting: compute the ordered [postId => score] map.
     *
     * @return array<int, float>
     */
    private function computeRelatedIds(Post $post, int $limit): array
    {
        $corpus = $this->loadCorpus($post->id);

        if ($corpus->isEmpty()) {
            return [];
        }

        // Tokenise every document (incl. the target).
        $targetTokens = $this->tokenize($this->documentFor($post));
        if ($targetTokens === []) {
            return [];
        }

        /** @var array<int, array<string, int>> $docTokenCounts */
        $docTokenCounts = $corpus
            ->mapWithKeys(fn (Post $p): array => [$p->id => $this->termCounts($this->tokenize($this->documentFor($p)))])
            ->all();
        $targetCounts = $this->termCounts($targetTokens);

        // IDF is computed over the corpus + the target document.
        $idf = $this->inverseDocumentFrequency(
            array_merge($docTokenCounts, [$post->id => $targetCounts])
        );

        $targetVector = $this->tfidfVector($targetCounts, $idf);

        $scores = [];
        foreach ($docTokenCounts as $id => $counts) {
            $similarity = $this->cosineSimilarity(
                $targetVector,
                $this->tfidfVector($counts, $idf),
            );

            if ($similarity > 0.0) {
                $scores[$id] = $similarity;
            }
        }

        arsort($scores); // highest similarity first

        return array_slice($scores, 0, $limit, preserve_keys: true);
    }

    /**
     * All other published posts form the comparison corpus.
     *
     * @return Collection<int, Post>
     */
    private function loadCorpus(int $excludePostId): Collection
    {
        return Post::query()
            ->published()
            ->where('id', '!=', $excludePostId)
            ->get(['id', 'title', 'body']);
    }

    /**
     * Build the comparable text for a post. The title is repeated to weight it
     * more heavily than body prose.
     */
    private function documentFor(Post $post): string
    {
        $title = str_repeat($post->title.' ', 3);

        return $title.$this->markdown->toPlainText((string) $post->body);
    }

    /**
     * @return list<string>
     */
    private function tokenize(string $text): array
    {
        $lower = mb_strtolower($text);
        preg_match_all('/[\p{L}\p{N}]+/u', $lower, $matches);

        return array_values(array_filter(
            $matches[0],
            fn (string $token): bool => mb_strlen($token) > 2 && !isset(self::STOPWORDS[$token]),
        ));
    }

    /**
     * @param  list<string>  $tokens
     * @return array<string, int>
     */
    private function termCounts(array $tokens): array
    {
        $counts = [];
        foreach ($tokens as $token) {
            $counts[$token] = ($counts[$token] ?? 0) + 1;
        }

        return $counts;
    }

    /**
     * IDF(term) = ln(N / documentFrequency(term)).
     *
     * @param  array<int, array<string, int>>  $documents
     * @return array<string, float>
     */
    private function inverseDocumentFrequency(array $documents): array
    {
        $n = count($documents);
        $documentFrequency = [];

        foreach ($documents as $counts) {
            foreach (array_keys($counts) as $term) {
                $documentFrequency[$term] = ($documentFrequency[$term] ?? 0) + 1;
            }
        }

        $idf = [];
        foreach ($documentFrequency as $term => $df) {
            $idf[$term] = log($n / $df);
        }

        return $idf;
    }

    /**
     * TF-IDF vector: (term count / doc length) * IDF(term).
     *
     * @param  array<string, int>  $counts
     * @param  array<string, float>  $idf
     * @return array<string, float>
     */
    private function tfidfVector(array $counts, array $idf): array
    {
        $length = array_sum($counts);
        if ($length === 0) {
            return [];
        }

        $vector = [];
        foreach ($counts as $term => $count) {
            $weight = ($count / $length) * ($idf[$term] ?? 0.0);
            if ($weight !== 0.0) {
                $vector[$term] = $weight;
            }
        }

        return $vector;
    }

    /**
     * Cosine similarity between two sparse vectors ∈ [0, 1].
     *
     * @param  array<string, float>  $a
     * @param  array<string, float>  $b
     */
    private function cosineSimilarity(array $a, array $b): float
    {
        if ($a === [] || $b === []) {
            return 0.0;
        }

        // Dot product over the smaller vector's keys.
        [$small, $large] = count($a) <= count($b) ? [$a, $b] : [$b, $a];

        $dot = 0.0;
        foreach ($small as $term => $weight) {
            if (isset($large[$term])) {
                $dot += $weight * $large[$term];
            }
        }

        if ($dot === 0.0) {
            return 0.0;
        }

        $magA = sqrt(array_sum(array_map(static fn (float $w): float => $w * $w, $a)));
        $magB = sqrt(array_sum(array_map(static fn (float $w): float => $w * $w, $b)));

        if ($magA === 0.0 || $magB === 0.0) {
            return 0.0;
        }

        return $dot / ($magA * $magB);
    }

    /**
     * Invalidate ALL cached recommendations at once.
     *
     * Editing one post changes the corpus for every other post, so per-key
     * deletion isn't enough. We bump a version counter that namespaces every
     * cache key — old entries are instantly orphaned and expire via TTL. This
     * works on any cache store (no tag support required). Called by PostObserver.
     */
    public function flush(): void
    {
        $this->cache->forever($this->versionKey(), $this->version() + 1);
    }

    private function cacheKey(int $postId, int $limit): string
    {
        return "related_posts:v{$this->version()}:{$postId}:{$limit}";
    }

    private function version(): int
    {
        $version = $this->cache->get($this->versionKey());

        if ($version === null) {
            $this->cache->forever($this->versionKey(), 1);

            return 1;
        }

        return (int) $version;
    }

    private function versionKey(): string
    {
        return 'related_posts:version';
    }
}
