package com.crawler.core;

import com.crawler.config.CrawlerConfig;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Handles HTTP fetching of web pages using Jsoup.
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable timeout and body size limits</li>
 *   <li>Custom User-Agent</li>
 *   <li>SSL certificate validation</li>
 *   <li>Retry support with exponential backoff</li>
 * </ul>
 */
public class PageFetcher { // |su:48 HTTP client - fetches pages with Jsoup, handles retries

    private static final Logger logger = LoggerFactory.getLogger(PageFetcher.class);

    private final CrawlerConfig config;
    private final int maxRetries; // |su:49 Retry count for transient failures (timeouts, 5xx errors)

    public PageFetcher(CrawlerConfig config) {
        this.config = config;
        this.maxRetries = config.getMaxRetries();
    }

    /**
     * Fetch a page from the given URL.
     *
     * @param url The URL to fetch
     * @return FetchResult containing the document and metadata
     * @throws IOException if the fetch fails after all retries
     */
    public FetchResult fetch(String url) throws IOException { // |su:50 Main fetch method with retry loop
        IOException lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return doFetch(url, attempt);
            } catch (SocketTimeoutException e) { // |su:51 Timeout = retryable (network issue)
                lastException = e;
                logger.warn("Timeout fetching URL (attempt {}): {}", attempt + 1, url);
                sleepBeforeRetry(attempt);
            } catch (org.jsoup.HttpStatusException e) {
                // |su:52 4xx = client error (bad URL) - don't retry, it won't succeed
                if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                    return new FetchResult(null, e.getStatusCode(), 0, false);
                }
                // |su:53 5xx = server error - retryable (server might recover)
                lastException = e;
                logger.warn("HTTP error {} fetching URL (attempt {}): {}",
                        e.getStatusCode(), attempt + 1, url);
                sleepBeforeRetry(attempt);
            } catch (IOException e) {
                lastException = e;
                logger.warn("IO error fetching URL (attempt {}): {} - {}",
                        attempt + 1, url, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }

        throw lastException != null ? lastException :
                new IOException("Failed to fetch after " + maxRetries + " attempts: " + url);
    }

    private FetchResult doFetch(String url, int attempt) throws IOException {
        logger.debug("Fetching URL (attempt {}): {}", attempt + 1, url);

        Connection connection = Jsoup.connect(url)
                .userAgent(config.getUserAgent())
                .timeout(config.getRequestTimeoutMs())
                .maxBodySize(config.getMaxBodySizeBytes())
                .followRedirects(true)
                .ignoreHttpErrors(false);

        Connection.Response response = connection.execute();

        // Get body bytes before parsing (parse() consumes the stream)
        byte[] bodyBytes = response.bodyAsBytes();
        int contentLength = bodyBytes != null ? bodyBytes.length : 0;

        // Parse the response body into a Document
        // Use UTF-8 as default if charset is not specified
        Charset charset = response.charset() != null ? Charset.forName(response.charset()) : StandardCharsets.UTF_8;
        String bodyContent = (bodyBytes != null && bodyBytes.length > 0)
                ? new String(bodyBytes, charset)
                : "";
        Document document = Jsoup.parse(bodyContent, url);

        int statusCode = response.statusCode();

        logger.debug("Fetched URL: {} (status={}, size={})", url, statusCode, contentLength);

        return new FetchResult(document, statusCode, contentLength, true);
    }

    private void sleepBeforeRetry(int attempt) { // |su:54 Exponential backoff: 1s, 2s, 4s delays
        long delay = config.getRetryBaseDelayMs() * (long) Math.pow(2, attempt); // 2^attempt
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Result of a page fetch operation.
     */
    public record FetchResult(
            Document document,
            int statusCode,
            int contentLength,
            boolean isSuccess
    ) {
    }
}
