package com.crawler.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ML-based relevance scoring for crawled content.
 *
 * <p>Combines multiple signals to score page relevance:
 * <ul>
 *   <li>TF-IDF based keyword relevance</li>
 *   <li>Title match scoring</li>
 *   <li>Content length normalization</li>
 *   <li>Link popularity (incoming links)</li>
 * </ul>
 */
public class RelevanceScorer { // |su:77 Multi-signal relevance model - combines 4 features into single score

    private static final Logger logger = LoggerFactory.getLogger(RelevanceScorer.class);

    // |su:78 Feature weights - tune these to prioritize different signals
    private static final double WEIGHT_TFIDF = 0.4; // 40% - keyword match via TF-IDF
    private static final double WEIGHT_TITLE = 0.25; // 25% - title quality/keyword presence
    private static final double WEIGHT_LENGTH = 0.15; // 15% - content depth
    private static final double WEIGHT_LINKS = 0.2; // 20% - link popularity (PageRank-lite)

    // Reference to TF-IDF calculator
    private final TfIdfCalculator tfidfCalculator;

    // Target keywords for relevance (if any)
    private final Set<String> targetKeywords;

    // Link counts: URL -> incoming link count
    private final Map<String, Integer> incomingLinks;

    public RelevanceScorer(TfIdfCalculator tfidfCalculator) {
        this.tfidfCalculator = tfidfCalculator;
        this.targetKeywords = new HashSet<>();
        this.incomingLinks = new HashMap<>();
    }

    /**
     * Set target keywords for relevance scoring.
     *
     * @param keywords Keywords that indicate relevant content
     */
    public void setTargetKeywords(Collection<String> keywords) {
        targetKeywords.clear();
        for (String kw : keywords) {
            targetKeywords.add(kw.toLowerCase());
        }
        logger.info("Set {} target keywords for scoring", targetKeywords.size());
    }

    /**
     * Record an incoming link to a URL.
     *
     * @param targetUrl The URL being linked to
     */
    public void recordIncomingLink(String targetUrl) {
        incomingLinks.merge(targetUrl, 1, Integer::sum);
    }

    /**
     * Calculate relevance score for a document.
     *
     * @param documentId Unique identifier (usually URL)
     * @param title      Page title
     * @param content    Preprocessed text content
     * @param terms      List of terms from content
     * @return Relevance score between 0.0 and 1.0
     */
    public double score(String documentId, String title, String content, List<String> terms) { // |su:79 Main scoring: weighted sum of features
        double tfidfScore = calculateTfIdfScore(documentId, terms);
        double titleScore = calculateTitleScore(title);
        double lengthScore = calculateLengthScore(content);
        double linkScore = calculateLinkScore(documentId);

        // |su:80 Linear combination: totalScore = w1*f1 + w2*f2 + w3*f3 + w4*f4
        double totalScore = (WEIGHT_TFIDF * tfidfScore) +
                           (WEIGHT_TITLE * titleScore) +
                           (WEIGHT_LENGTH * lengthScore) +
                           (WEIGHT_LINKS * linkScore);

        double finalScore = Math.min(1.0, Math.max(0.0, totalScore));

        // Log score breakdown for debugging
        int contentSize = content != null ? content.length() : 0;
        String sizeStr = contentSize > 1000 ? (contentSize / 1000) + "KB" : contentSize + "B";
        logger.info("SCORE {} [tfidf:{} title:{} len:{} links:{}] size={} | {}",
                String.format("%.2f", finalScore),
                String.format("%.2f", tfidfScore),
                String.format("%.2f", titleScore),
                String.format("%.2f", lengthScore),
                String.format("%.2f", linkScore),
                sizeStr,
                truncateUrl(documentId));

        return finalScore;
    }

    private String truncateUrl(String url) {
        if (url == null) return "";
        // Remove protocol and show last 60 chars
        String clean = url.replaceFirst("https?://", "");
        return clean.length() > 60 ? "..." + clean.substring(clean.length() - 57) : clean;
    }

    /**
     * Calculate TF-IDF based relevance score.
     */
    private double calculateTfIdfScore(String documentId, List<String> terms) {
        if (targetKeywords.isEmpty()) {
            // No target keywords - return average TF-IDF as indicator of content richness
            Map<String, Double> tfidfVector = tfidfCalculator.getTfIdfVector(documentId);
            if (tfidfVector.isEmpty()) {
                return 0.0;
            }
            double avgTfidf = tfidfVector.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            return Math.min(1.0, avgTfidf * 2); // Scale to 0-1 range
        }

        // Calculate how many target keywords are present with their TF-IDF weights
        double score = 0.0;
        int matchedKeywords = 0;

        for (String keyword : targetKeywords) {
            double tfidf = tfidfCalculator.calculateTfIdf(documentId, keyword);
            if (tfidf > 0) {
                score += tfidf;
                matchedKeywords++;
            }
        }

        if (matchedKeywords == 0) {
            return 0.0;
        }

        // Normalize by number of target keywords
        return Math.min(1.0, score / targetKeywords.size());
    }

    /**
     * Calculate title relevance score.
     */
    private double calculateTitleScore(String title) {
        if (title == null || title.isBlank()) {
            return 0.0;
        }

        if (targetKeywords.isEmpty()) {
            // No target keywords - score based on title quality
            // Good titles are typically 30-70 characters
            int len = title.length();
            if (len >= 30 && len <= 70) {
                return 1.0;
            } else if (len >= 10 && len <= 100) {
                return 0.7;
            } else {
                return 0.3;
            }
        }

        // Check for keyword matches in title
        String lowerTitle = title.toLowerCase();
        int matches = 0;
        for (String keyword : targetKeywords) {
            if (lowerTitle.contains(keyword)) {
                matches++;
            }
        }

        return (double) matches / targetKeywords.size();
    }

    /**
     * Calculate content length score.
     * Longer content (up to a point) indicates more substantive pages.
     */
    private double calculateLengthScore(String content) {
        if (content == null || content.isBlank()) {
            return 0.0;
        }

        int length = content.length();

        // Ideal content length: 1000-5000 characters
        if (length < 200) {
            return 0.1; // Too short
        } else if (length < 500) {
            return 0.4;
        } else if (length < 1000) {
            return 0.7;
        } else if (length <= 5000) {
            return 1.0; // Ideal range
        } else if (length <= 10000) {
            return 0.9; // Still good
        } else {
            return 0.7; // Very long, might be less focused
        }
    }

    /**
     * Calculate link popularity score.
     */
    private double calculateLinkScore(String url) { // |su:81 Link popularity - pages linked by others are more important
        Integer linkCount = incomingLinks.get(url);
        if (linkCount == null || linkCount == 0) {
            return 0.0;
        }

        // |su:82 Logarithmic scaling: diminishing returns - 10→100 links adds less than 1→10
        // 1 link = 0.3, 10 links = 0.6, 100 links = 0.9
        return Math.min(1.0, Math.log10(linkCount + 1) / 3);
    }

    /**
     * Get the current scoring statistics.
     */
    public String getStats() {
        return String.format("RelevanceScorer[targetKeywords=%d, trackedUrls=%d]",
                targetKeywords.size(), incomingLinks.size());
    }
}
