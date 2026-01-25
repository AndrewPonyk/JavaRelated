package com.crawler.core;

import com.crawler.util.UrlNormalizer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts and filters links from HTML documents.
 *
 * <p>Features:
 * <ul>
 *   <li>Extracts href attributes from anchor tags</li>
 *   <li>Resolves relative URLs to absolute</li>
 *   <li>Filters out non-HTTP links</li>
 *   <li>Handles malformed URLs gracefully</li>
 * </ul>
 */
public class LinkExtractor { // |su:127 Discovers new URLs from HTML - extracts <a href="..."> links

    private static final Logger logger = LoggerFactory.getLogger(LinkExtractor.class);

    /**
     * Extract all valid links from a document.
     *
     * @param document The parsed HTML document
     * @param baseUrl  The URL of the document (for resolving relative links)
     * @return List of extracted absolute URLs
     */
    public List<String> extract(Document document, String baseUrl) { // |su:128 Extract all valid links from HTML page
        List<String> links = new ArrayList<>();

        if (document == null) {
            return links;
        }

        // |su:129 CSS selector: a[href] = all anchor tags with href attribute
        Elements anchors = document.select("a[href]");

        for (Element anchor : anchors) {
            try {
                // |su:130 absUrl() converts relative URLs: "/page" → "https://example.com/page"
                String href = anchor.absUrl("href");

                if (href.isEmpty()) {
                    continue;
                }

                // Normalize the URL
                String normalizedUrl = UrlNormalizer.normalize(href);
                if (normalizedUrl == null) {
                    continue;
                }

                // Filter valid URLs
                if (isValidLink(normalizedUrl)) {
                    links.add(normalizedUrl);
                }
            } catch (Exception e) {
                logger.trace("Error extracting link from element: {}", anchor, e);
            }
        }

        logger.debug("Extracted {} links from {}", links.size(), baseUrl);
        return links;
    }

    /**
     * Extract links matching a specific domain.
     *
     * @param document The parsed HTML document
     * @param baseUrl  The URL of the document
     * @param domain   Domain to filter (e.g., "example.com")
     * @return List of URLs matching the domain
     */
    public List<String> extractForDomain(Document document, String baseUrl, String domain) {
        List<String> allLinks = extract(document, baseUrl);
        List<String> domainLinks = new ArrayList<>();

        for (String link : allLinks) {
            if (matchesDomain(link, domain)) {
                domainLinks.add(link);
            }
        }

        return domainLinks;
    }

    private boolean isValidLink(String url) {
        // Only allow HTTP and HTTPS
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }

        // Skip fragments-only links
        if (url.contains("#") && url.indexOf("#") == url.lastIndexOf("/") + 1) {
            return false;
        }

        // Skip mailto, tel, javascript links
        String lower = url.toLowerCase();
        if (lower.startsWith("mailto:") ||
            lower.startsWith("tel:") ||
            lower.startsWith("javascript:")) {
            return false;
        }

        return true;
    }

    private boolean matchesDomain(String url, String domain) {
        try {
            java.net.URL u = new java.net.URL(url);
            String host = u.getHost().toLowerCase();
            domain = domain.toLowerCase();

            // Match exact domain or subdomain
            return host.equals(domain) || host.endsWith("." + domain);
        } catch (Exception e) {
            return false;
        }
    }
}
