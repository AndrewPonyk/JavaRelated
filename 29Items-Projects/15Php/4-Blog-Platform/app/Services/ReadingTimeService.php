<?php

declare(strict_types=1);

namespace App\Services;

/**
 * Estimates reading time for a piece of content.
 *
 * Model: words / words-per-minute, rounded up to the nearest minute (always at
 * least 1). Images add a small fixed cost (people pause to look), using the
 * common Medium-style 12s-for-first-image-decreasing heuristic, simplified here
 * to a flat per-image seconds cost.
 *
 * Pure and dependency-free → trivially unit-testable (see ReadingTimeServiceTest).
 */
class ReadingTimeService
{
    private const SECONDS_PER_IMAGE = 10;

    public function __construct(
        private readonly int $wordsPerMinute = 225,
    ) {}

    /**
     * Estimate reading time in whole minutes (minimum 1).
     *
     * @param  string  $text  Plain text (strip Markdown/HTML first).
     * @param  int  $imageCount  Number of images in the content.
     */
    public function minutes(string $text, int $imageCount = 0): int
    {
        $words = $this->countWords($text);

        if ($words === 0 && $imageCount === 0) {
            return 1;
        }

        $wordSeconds = ($words / max($this->wordsPerMinute, 1)) * 60;
        $imageSeconds = $imageCount * self::SECONDS_PER_IMAGE;

        $totalMinutes = (int) ceil(($wordSeconds + $imageSeconds) / 60);

        return max($totalMinutes, 1);
    }

    /**
     * Human-friendly label, e.g. "4 min read".
     */
    public function label(string $text, int $imageCount = 0): string
    {
        return $this->minutes($text, $imageCount).' min read';
    }

    private function countWords(string $text): int
    {
        $normalised = trim(preg_replace('/\s+/u', ' ', $text) ?? '');

        if ($normalised === '') {
            return 0;
        }

        // str_word_count misses Unicode; count whitespace-separated tokens instead.
        return count(explode(' ', $normalised));
    }
}
