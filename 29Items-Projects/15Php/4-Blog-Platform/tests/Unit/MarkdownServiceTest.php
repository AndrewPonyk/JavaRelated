<?php

declare(strict_types=1);

namespace Tests\Unit;

use App\Services\MarkdownService;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

/**
 * Exercises the Markdown → safe-HTML pipeline, with emphasis on the XSS cases
 * that matter most for a Markdown-driven blog.
 */
class MarkdownServiceTest extends TestCase
{
    private MarkdownService $markdown;

    protected function setUp(): void
    {
        parent::setUp();
        $this->markdown = new MarkdownService;
    }

    #[Test]
    public function it_renders_basic_markdown(): void
    {
        $html = $this->markdown->toHtml("# Title\n\nSome **bold** and *italic* text.");

        $this->assertStringContainsString('<h1>Title</h1>', $html);
        $this->assertStringContainsString('<strong>bold</strong>', $html);
        $this->assertStringContainsString('<em>italic</em>', $html);
    }

    #[Test]
    public function it_renders_github_flavoured_tables(): void
    {
        $html = $this->markdown->toHtml("| A | B |\n|---|---|\n| 1 | 2 |");

        $this->assertStringContainsString('<table>', $html);
        $this->assertStringContainsString('<td>1</td>', $html);
    }

    #[Test]
    public function it_strips_raw_script_tags(): void
    {
        $html = $this->markdown->toHtml("Hello <script>alert('xss')</script> world");

        // The dangerous <script> element must be gone. Its inner text may survive
        // as inert plain text ("alert('xss')") — that is harmless and expected.
        $this->assertStringNotContainsString('<script', $html);
        $this->assertStringNotContainsString('</script>', $html);
    }

    #[Test]
    public function it_neutralises_javascript_links(): void
    {
        $html = $this->markdown->toHtml('[click me](javascript:alert(1))');

        $this->assertStringNotContainsString('javascript:', $html);
    }

    #[Test]
    public function it_strips_event_handler_attributes(): void
    {
        $html = $this->markdown->toHtml('<img src="x" onerror="alert(1)">');

        $this->assertStringNotContainsString('onerror', $html);
    }

    #[Test]
    public function to_plain_text_removes_all_markup(): void
    {
        $plain = $this->markdown->toPlainText("# Heading\n\nA paragraph with **bold**.");

        $this->assertStringNotContainsString('<', $plain);
        $this->assertStringNotContainsString('#', $plain);
        $this->assertStringContainsString('Heading', $plain);
        $this->assertStringContainsString('bold', $plain);
    }
}
