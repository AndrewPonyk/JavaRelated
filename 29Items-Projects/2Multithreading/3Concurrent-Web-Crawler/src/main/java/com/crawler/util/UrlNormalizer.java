package com.crawler.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * URL normalization for consistent deduplication.
 *
 * <p>Normalization rules:
 * <ul>
 *   <li>Convert to lowercase (scheme and host only)</li>
 *   <li>Remove default ports (80 for HTTP, 443 for HTTPS)</li>
 *   <li>Remove fragment identifiers (#...)</li>
 *   <li>Sort query parameters</li>
 *   <li>Remove trailing slashes (configurable)</li>
 *   <li>Decode percent-encoded characters where safe</li>
 *   <li>Remove common tracking parameters</li>
 * </ul>
 */
public class UrlNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(UrlNormalizer.class);

    // Common tracking parameters to remove
    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "ref", "source", "mc_cid", "mc_eid",
            "_ga", "_gid", "hsCtaTracking"
    );

    // Pattern for multiple consecutive slashes in path
    private static final Pattern MULTI_SLASH = Pattern.compile("/+");

    /**
     * Normalize a URL string.
     *
     * @param urlString URL to normalize
     * @return Normalized URL string, or null if invalid
     */
    public static String normalize(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return null;
        }

        try {
            // Trim whitespace
            urlString = urlString.trim();

            // Parse URL
            URL url = new URL(urlString);

            // Build normalized URL
            StringBuilder normalized = new StringBuilder();

            // Scheme (lowercase)
            String scheme = url.getProtocol().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null; // Only support HTTP/HTTPS
            }
            normalized.append(scheme).append("://");

            // Host (lowercase)
            String host = url.getHost().toLowerCase();
            if (host.isEmpty()) {
                return null;
            }
            normalized.append(host);

            // Port (remove default ports)
            int port = url.getPort();
            if (port != -1) {
                if ((scheme.equals("http") && port != 80) ||
                    (scheme.equals("https") && port != 443)) {
                    normalized.append(":").append(port);
                }
            }

            // Path
            String path = url.getPath();
            if (path.isEmpty()) {
                path = "/";
            } else {
                // Normalize path
                path = normalizePath(path);
            }
            normalized.append(path);

            // Query string (sorted, tracking params removed)
            String query = url.getQuery();
            if (query != null && !query.isEmpty()) {
                String normalizedQuery = normalizeQuery(query);
                if (!normalizedQuery.isEmpty()) {
                    normalized.append("?").append(normalizedQuery);
                }
            }

            // Fragment is intentionally omitted

            return normalized.toString();

        } catch (Exception e) {
            logger.trace("Failed to normalize URL: {}", urlString, e);
            return null;
        }
    }

    /**
     * Normalize URL path.
     */
    private static String normalizePath(String path) {
        // Decode percent-encoded characters
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Keep original if decoding fails
        }

        // Remove multiple consecutive slashes
        path = MULTI_SLASH.matcher(path).replaceAll("/");

        // Resolve . and .. segments
        path = resolvePathSegments(path);

        // Re-encode special characters
        try {
            // Encode each path segment separately
            String[] segments = path.split("/", -1);
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) encoded.append("/");
                if (!segments[i].isEmpty()) {
                    encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8)
                            .replace("+", "%20")
                            .replace("%2F", "/"));
                }
            }
            path = encoded.toString();
        } catch (Exception e) {
            // Keep original if encoding fails
        }

        return path.isEmpty() ? "/" : path;
    }

    /**
     * Resolve . and .. path segments.
     */
    private static String resolvePathSegments(String path) {
        Deque<String> segments = new ArrayDeque<>();

        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
            } else if (!segment.equals(".") && !segment.isEmpty()) {
                segments.addLast(segment);
            }
        }

        if (segments.isEmpty()) {
            return "/";
        }

        StringBuilder result = new StringBuilder();
        for (String segment : segments) {
            result.append("/").append(segment);
        }

        // Remove trailing slash for consistency (except for root path)
        // This ensures /page and /page/ are treated as the same URL
        String resultPath = result.toString();
        if (resultPath.length() > 1 && resultPath.endsWith("/")) {
            resultPath = resultPath.substring(0, resultPath.length() - 1);
        }

        return resultPath;
    }

    /**
     * Normalize query string: sort parameters, remove tracking params.
     */
    private static String normalizeQuery(String query) {
        Map<String, String> params = new TreeMap<>(); // TreeMap for sorted keys

        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            String key, value;
            if (eq > 0) {
                key = param.substring(0, eq);
                value = param.substring(eq + 1);
            } else {
                key = param;
                value = "";
            }

            // Skip tracking parameters
            if (TRACKING_PARAMS.contains(key.toLowerCase())) {
                continue;
            }

            // Decode and re-encode for consistency
            try {
                key = URLDecoder.decode(key, StandardCharsets.UTF_8);
                value = URLDecoder.decode(value, StandardCharsets.UTF_8);
                key = URLEncoder.encode(key, StandardCharsets.UTF_8);
                value = URLEncoder.encode(value, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Keep original if encoding fails
            }

            params.put(key, value);
        }

        if (params.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (result.length() > 0) {
                result.append("&");
            }
            result.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                result.append("=").append(entry.getValue());
            }
        }

        return result.toString();
    }

    /**
     * Extract domain from URL.
     *
     * @param url URL string
     * @return Domain name, or null if invalid
     */
    public static String extractDomain(String url) {
        try {
            return new URL(url).getHost().toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if two URLs are the same after normalization.
     */
    public static boolean areEquivalent(String url1, String url2) {
        String n1 = normalize(url1);
        String n2 = normalize(url2);
        return n1 != null && n1.equals(n2);
    }
}
