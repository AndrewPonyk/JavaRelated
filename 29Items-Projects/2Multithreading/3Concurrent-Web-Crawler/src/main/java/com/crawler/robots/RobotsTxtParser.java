package com.crawler.robots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parser for robots.txt files.
 *
 * <p>Supports:
 * <ul>
 *   <li>User-agent directives</li>
 *   <li>Allow/Disallow rules</li>
 *   <li>Crawl-delay directive</li>
 *   <li>Wildcard patterns (* and $)</li>
 * </ul>
 */
public class RobotsTxtParser { // |su:55 Parses robots.txt - website crawling rules (Allow/Disallow paths)

    private static final Logger logger = LoggerFactory.getLogger(RobotsTxtParser.class);

    private static final String USER_AGENT = "User-agent:";
    private static final String DISALLOW = "Disallow:";
    private static final String ALLOW = "Allow:";
    private static final String CRAWL_DELAY = "Crawl-delay:";

    /**
     * Parse robots.txt content.
     *
     * @param content   Raw robots.txt content
     * @param userAgent Our crawler's user agent
     * @return Parsed RobotsTxt rules
     */
    public RobotsTxt parse(String content, String userAgent) {
        List<Rule> rules = new ArrayList<>();
        double crawlDelay = 0.0;
        boolean inRelevantSection = false;
        boolean foundSpecificAgent = false;

        if (content == null || content.isBlank()) {
            return new RobotsTxt(rules, crawlDelay);
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }

                // Skip full-line comments
                if (line.startsWith("#")) {
                    continue;
                }

                // Strip inline comments
                int commentIndex = line.indexOf('#');
                if (commentIndex > 0) {
                    line = line.substring(0, commentIndex).trim();
                }

                // Handle User-agent directive
                if (line.toLowerCase().startsWith(USER_AGENT.toLowerCase())) {
                    String agent = line.substring(USER_AGENT.length()).trim().toLowerCase();

                    // Check if this section applies to us
                    if (agent.equals("*")) {
                        // Wildcard applies to everyone, but specific rules take precedence
                        if (!foundSpecificAgent) {
                            inRelevantSection = true;
                        }
                    } else if (userAgent.toLowerCase().contains(agent) ||
                               agent.contains(userAgent.toLowerCase().split("/")[0])) {
                        // Specific match for our user agent
                        inRelevantSection = true;
                        foundSpecificAgent = true;
                        // Clear generic rules if we found specific ones
                        rules.clear();
                        crawlDelay = 0.0;
                    } else {
                        // Different agent, skip this section unless we haven't found any matching
                        if (foundSpecificAgent) {
                            inRelevantSection = false;
                        }
                    }
                    continue;
                }

                // Only process rules in relevant sections
                if (!inRelevantSection) {
                    continue;
                }

                // Handle Allow directive
                if (line.toLowerCase().startsWith(ALLOW.toLowerCase())) {
                    String path = line.substring(ALLOW.length()).trim();
                    if (!path.isEmpty()) {
                        rules.add(new Rule(RuleType.ALLOW, path, createPattern(path)));
                    }
                }
                // Handle Disallow directive
                else if (line.toLowerCase().startsWith(DISALLOW.toLowerCase())) {
                    String path = line.substring(DISALLOW.length()).trim();
                    if (!path.isEmpty()) {
                        rules.add(new Rule(RuleType.DISALLOW, path, createPattern(path)));
                    } else {
                        // Empty Disallow means allow all
                        rules.add(new Rule(RuleType.ALLOW, "/", Pattern.compile(".*")));
                    }
                }
                // Handle Crawl-delay directive
                else if (line.toLowerCase().startsWith(CRAWL_DELAY.toLowerCase())) {
                    String delayStr = line.substring(CRAWL_DELAY.length()).trim();
                    try {
                        crawlDelay = Double.parseDouble(delayStr);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid Crawl-delay value: {}", delayStr);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing robots.txt", e);
        }

        logger.debug("Parsed robots.txt: {} rules, crawl-delay={}", rules.size(), crawlDelay);
        return new RobotsTxt(rules, crawlDelay);
    }

    /**
     * Create a regex pattern from robots.txt path pattern.
     *
     * <p>Supports:
     * <ul>
     *   <li>* - matches any sequence of characters</li>
     *   <li>$ - matches end of URL</li>
     * </ul>
     */
    private Pattern createPattern(String path) {
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '$':
                    if (i == path.length() - 1) {
                        regex.append("$");
                    } else {
                        regex.append("\\$");
                    }
                    break;
                case '.':
                case '?':
                case '+':
                case '^':
                case '[':
                case ']':
                case '(':
                case ')':
                case '{':
                case '}':
                case '|':
                case '\\':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
            }
        }

        return Pattern.compile(regex.toString());
    }

    /**
     * Parsed robots.txt representation.
     */
    public record RobotsTxt(List<Rule> rules, double crawlDelay) {

        /**
         * Check if a URL path is allowed.
         *
         * @param path URL path to check
         * @return true if allowed, false if disallowed
         */
        public boolean isAllowed(String path) {
            Rule matchedRule = null;
            int longestMatch = -1;

            for (Rule rule : rules) {
                if (rule.pattern().matcher(path).find()) {
                    if (rule.path().length() > longestMatch) {
                        longestMatch = rule.path().length();
                        matchedRule = rule;
                    }
                }
            }

            if (matchedRule == null) {
                return true;
            }

            return matchedRule.type() == RuleType.ALLOW;
        }

        /**
         * Get crawl delay in milliseconds.
         */
        public long getCrawlDelayMs() {
            return (long) (crawlDelay * 1000);
        }
    }

    /**
     * A single Allow/Disallow rule.
     */
    public record Rule(RuleType type, String path, Pattern pattern) {
    }

    /**
     * Rule type enumeration.
     */
    public enum RuleType {
        ALLOW,
        DISALLOW
    }
}
