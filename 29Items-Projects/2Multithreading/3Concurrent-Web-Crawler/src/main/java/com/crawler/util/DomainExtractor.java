package com.crawler.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Set;

/**
 * Utility for extracting and working with domain names.
 */
public class DomainExtractor {

    private static final Logger logger = LoggerFactory.getLogger(DomainExtractor.class);

    // Common public suffixes (simplified - in production use a full PSL)
    private static final Set<String> PUBLIC_SUFFIXES = Set.of(
            "com", "org", "net", "edu", "gov", "mil",
            "co.uk", "org.uk", "ac.uk",
            "com.au", "org.au", "net.au",
            "co.jp", "or.jp", "ne.jp",
            "de", "fr", "it", "es", "nl", "be", "ch", "at",
            "io", "ai", "app", "dev"
    );

    /**
     * Extract domain from URL.
     *
     * @param urlString URL string
     * @return Domain name (e.g., "example.com")
     */
    public static String extractDomain(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.getHost().toLowerCase();
        } catch (Exception e) {
            logger.trace("Failed to extract domain from: {}", urlString);
            return null;
        }
    }

    /**
     * Extract the registrable domain (domain + public suffix).
     * E.g., "www.sub.example.com" -> "example.com"
     *
     * @param urlString URL string
     * @return Registrable domain
     */
    public static String extractRegistrableDomain(String urlString) {
        String host = extractDomain(urlString);
        if (host == null) {
            return null;
        }

        return extractRegistrableDomainFromHost(host);
    }

    /**
     * Extract registrable domain from a hostname.
     *
     * @param host Hostname
     * @return Registrable domain
     */
    public static String extractRegistrableDomainFromHost(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }

        String[] parts = host.split("\\.");
        if (parts.length <= 2) {
            return host;
        }

        // Check for compound TLDs (like co.uk)
        if (parts.length >= 3) {
            String compoundTld = parts[parts.length - 2] + "." + parts[parts.length - 1];
            if (PUBLIC_SUFFIXES.contains(compoundTld)) {
                // Return domain + compound TLD
                return parts[parts.length - 3] + "." + compoundTld;
            }
        }

        // Return domain + simple TLD
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    /**
     * Check if a URL belongs to a specific domain.
     *
     * @param url    URL to check
     * @param domain Domain to match
     * @return true if URL is from the domain or its subdomains
     */
    public static boolean matchesDomain(String url, String domain) {
        String urlDomain = extractDomain(url);
        if (urlDomain == null || domain == null) {
            return false;
        }

        domain = domain.toLowerCase();
        return urlDomain.equals(domain) || urlDomain.endsWith("." + domain);
    }

    /**
     * Check if two URLs are from the same domain.
     *
     * @param url1 First URL
     * @param url2 Second URL
     * @return true if same domain
     */
    public static boolean sameDomain(String url1, String url2) {
        String domain1 = extractDomain(url1);
        String domain2 = extractDomain(url2);
        return domain1 != null && domain1.equals(domain2);
    }

    /**
     * Check if two URLs are from the same registrable domain.
     *
     * @param url1 First URL
     * @param url2 Second URL
     * @return true if same registrable domain
     */
    public static boolean sameRegistrableDomain(String url1, String url2) {
        String rd1 = extractRegistrableDomain(url1);
        String rd2 = extractRegistrableDomain(url2);
        return rd1 != null && rd1.equals(rd2);
    }

    /**
     * Get the protocol (scheme) from a URL.
     *
     * @param urlString URL string
     * @return Protocol (http or https)
     */
    public static String getProtocol(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.getProtocol().toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if URL uses HTTPS.
     *
     * @param urlString URL string
     * @return true if HTTPS
     */
    public static boolean isHttps(String urlString) {
        return "https".equals(getProtocol(urlString));
    }
}
