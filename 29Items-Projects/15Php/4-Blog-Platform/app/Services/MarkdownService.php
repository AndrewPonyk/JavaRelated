<?php

declare(strict_types=1);

namespace App\Services;

use HTMLPurifier;
use HTMLPurifier_Config;
use League\CommonMark\Environment\Environment;
use League\CommonMark\Exception\CommonMarkException;
use League\CommonMark\Extension\Autolink\AutolinkExtension;
use League\CommonMark\Extension\CommonMark\CommonMarkCoreExtension;
use League\CommonMark\Extension\GithubFlavoredMarkdownExtension;
use League\CommonMark\MarkdownConverter;

/**
 * Converts Markdown source into SAFE HTML.
 *
 * Security posture (see docs/ARCHITECTURE.md §2.5):
 *   1. CommonMark is configured with raw HTML **disabled** and unsafe links
 *      stripped — untrusted input cannot inject <script> via passthrough HTML.
 *   2. The output is then run through HTML Purifier against an allow-list as a
 *      defence-in-depth second pass.
 *
 * Render-on-save: call this when persisting a post and store the result in
 * `posts.body_html` so the read path never pays the parsing/sanitising cost.
 */
class MarkdownService
{
    private readonly MarkdownConverter $converter;

    private readonly HTMLPurifier $purifier;

    public function __construct()
    {
        $environment = new Environment([
            'html_input' => 'strip',          // drop any raw HTML in the source
            'allow_unsafe_links' => false,    // strip javascript: / data: links
            'max_nesting_level' => 50,
        ]);

        $environment->addExtension(new CommonMarkCoreExtension);
        $environment->addExtension(new GithubFlavoredMarkdownExtension); // tables, strikethrough, task lists
        $environment->addExtension(new AutolinkExtension);

        $this->converter = new MarkdownConverter($environment);
        $this->purifier = new HTMLPurifier($this->purifierConfig());
    }

    /**
     * Render Markdown to sanitised HTML, safe to emit with `{!! ... !!}`.
     */
    public function toHtml(string $markdown): string
    {
        try {
            $rawHtml = $this->converter->convert($markdown)->getContent();
        } catch (CommonMarkException) {
            // Never blow up a request over malformed Markdown — degrade to escaped text.
            return e($markdown);
        }

        return $this->purifier->purify($rawHtml);
    }

    /**
     * Strip Markdown/HTML down to plain text — handy for excerpts, meta
     * descriptions, and the TF-IDF corpus.
     */
    public function toPlainText(string $markdown): string
    {
        $html = $this->toHtml($markdown);

        return trim(html_entity_decode(strip_tags($html)));
    }

    private function purifierConfig(): HTMLPurifier_Config
    {
        $config = HTMLPurifier_Config::createDefault();

        $config->set('HTML.Allowed', implode(',', [
            'p', 'br', 'hr', 'blockquote',
            'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
            'strong', 'em', 'del', 'code', 'pre',
            'ul', 'ol', 'li',
            'a[href|title|rel]',
            'img[src|alt|title|width|height]',
            'table', 'thead', 'tbody', 'tr', 'th', 'td',
        ]));

        // Force safe rel + open external links in a new tab.
        $config->set('HTML.TargetBlank', true);
        $config->set('Attr.AllowedRel', ['noopener', 'noreferrer', 'nofollow']);
        $config->set('URI.AllowedSchemes', ['http' => true, 'https' => true, 'mailto' => true]);

        // Cache compiled definitions for speed (writable path).
        $cachePath = storage_path('app/htmlpurifier');
        if (!is_dir($cachePath)) {
            @mkdir($cachePath, 0775, true);
        }
        $config->set('Cache.SerializerPath', $cachePath);

        return $config;
    }
}
