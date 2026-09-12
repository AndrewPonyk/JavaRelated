<?php

declare(strict_types=1);

namespace Tests\Unit;

use App\Services\ReadingTimeService;
use PHPUnit\Framework\Attributes\Test;
use PHPUnit\Framework\TestCase;

/**
 * Pure unit tests — no framework boot, no DB. The service is dependency-free
 * by design, which is exactly why this logic lives in a service.
 */
class ReadingTimeServiceTest extends TestCase
{
    #[Test]
    public function it_returns_at_least_one_minute_for_empty_or_short_text(): void
    {
        $service = new ReadingTimeService(wordsPerMinute: 225);

        $this->assertSame(1, $service->minutes(''));
        $this->assertSame(1, $service->minutes('Just a few words here.'));
    }

    #[Test]
    public function it_estimates_minutes_from_word_count(): void
    {
        $service = new ReadingTimeService(wordsPerMinute: 200);

        // 600 words at 200 wpm = 3 minutes exactly.
        $text = str_repeat('word ', 600);

        $this->assertSame(3, $service->minutes($text));
    }

    #[Test]
    public function it_rounds_partial_minutes_up(): void
    {
        $service = new ReadingTimeService(wordsPerMinute: 200);

        // 250 words at 200 wpm = 1.25 min → ceil → 2.
        $text = str_repeat('word ', 250);

        $this->assertSame(2, $service->minutes($text));
    }

    #[Test]
    public function images_add_to_the_estimate(): void
    {
        $service = new ReadingTimeService(wordsPerMinute: 200);
        $text = str_repeat('word ', 200); // exactly 1 minute of text

        // 10 images * 10s = 100s extra → 60s + 100s = 160s → ceil(160/60) = 3.
        $this->assertSame(3, $service->minutes($text, imageCount: 10));
    }

    #[Test]
    public function it_produces_a_human_label(): void
    {
        $service = new ReadingTimeService(wordsPerMinute: 200);

        $this->assertSame('3 min read', $service->label(str_repeat('word ', 600)));
    }
}
