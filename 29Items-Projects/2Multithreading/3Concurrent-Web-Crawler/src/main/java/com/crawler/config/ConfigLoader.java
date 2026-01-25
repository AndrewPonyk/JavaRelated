package com.crawler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads configuration from multiple sources in order of priority:
 * 1. Default values (in CrawlerConfig)
 * 2. application.properties from classpath
 * 3. External config file (if specified)
 * 4. Environment variables (highest priority)
 */
public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String DEFAULT_CONFIG_FILE = "application.properties";

    /**
     * Load configuration with default settings.
     */
    public static CrawlerConfig load() {
        return load(null);
    }

    /**
     * Load configuration from specified file path, falling back to defaults.
     *
     * @param configPath Path to external config file, or null for defaults
     * @return Populated CrawlerConfig instance
     */
    public static CrawlerConfig load(String configPath) {
        CrawlerConfig config = new CrawlerConfig();
        Properties props = new Properties();

        // 1. Load from classpath
        try (InputStream is = ConfigLoader.class.getClassLoader()
                .getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                logger.info("Loaded configuration from classpath: {}", DEFAULT_CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.warn("Could not load default config from classpath", e);
        }

        // 2. Load from external file if specified
        if (configPath != null) {
            try (FileInputStream fis = new FileInputStream(configPath)) {
                props.load(fis);
                logger.info("Loaded configuration from: {}", configPath);
            } catch (IOException e) {
                logger.warn("Could not load config from: {}", configPath, e);
            }
        }

        // 3. Apply properties to config
        applyProperties(config, props);

        // 4. Override with environment variables
        applyEnvironmentVariables(config);

        logger.info("Configuration loaded: {}", config);
        return config;
    }

    private static void applyProperties(CrawlerConfig config, Properties props) {
        if (props.containsKey("crawler.threads")) {
            config.setThreadCount(Integer.parseInt(props.getProperty("crawler.threads")));
        }
        if (props.containsKey("crawler.maxConnections")) {
            config.setMaxConnections(Integer.parseInt(props.getProperty("crawler.maxConnections")));
        }
        if (props.containsKey("crawler.maxPages")) {
            config.setMaxPages(Integer.parseInt(props.getProperty("crawler.maxPages")));
        }
        if (props.containsKey("crawler.maxDepth")) {
            config.setMaxDepth(Integer.parseInt(props.getProperty("crawler.maxDepth")));
        }
        if (props.containsKey("crawler.defaultDelayMs")) {
            config.setDefaultDelayMs(Long.parseLong(props.getProperty("crawler.defaultDelayMs")));
        }
        if (props.containsKey("crawler.requestTimeoutMs")) {
            config.setRequestTimeoutMs(Integer.parseInt(props.getProperty("crawler.requestTimeoutMs")));
        }
        if (props.containsKey("crawler.userAgent")) {
            config.setUserAgent(props.getProperty("crawler.userAgent"));
        }
        if (props.containsKey("crawler.respectRobotsTxt")) {
            config.setRespectRobotsTxt(Boolean.parseBoolean(props.getProperty("crawler.respectRobotsTxt")));
        }
        if (props.containsKey("db.path")) {
            config.setDatabasePath(props.getProperty("db.path"));
        }
        if (props.containsKey("crawler.maxRetries")) {
            config.setMaxRetries(Integer.parseInt(props.getProperty("crawler.maxRetries")));
        }
        if (props.containsKey("crawler.allowedDomains")) {
            String domains = props.getProperty("crawler.allowedDomains");
            if (!domains.isBlank()) {
                config.setAllowedDomains(domains.split(","));
            }
        }
        if (props.containsKey("crawler.blockedDomains")) {
            String domains = props.getProperty("crawler.blockedDomains");
            if (!domains.isBlank()) {
                config.setBlockedDomains(domains.split(","));
            }
        }
    }

    private static void applyEnvironmentVariables(CrawlerConfig config) {
        String threads = System.getenv("CRAWLER_THREADS");
        if (threads != null) {
            config.setThreadCount(Integer.parseInt(threads));
        }

        String maxConnections = System.getenv("CRAWLER_MAX_CONNECTIONS");
        if (maxConnections != null) {
            config.setMaxConnections(Integer.parseInt(maxConnections));
        }

        String maxPages = System.getenv("CRAWLER_MAX_PAGES");
        if (maxPages != null) {
            config.setMaxPages(Integer.parseInt(maxPages));
        }

        String defaultDelay = System.getenv("CRAWLER_DEFAULT_DELAY_MS");
        if (defaultDelay != null) {
            config.setDefaultDelayMs(Long.parseLong(defaultDelay));
        }

        String userAgent = System.getenv("CRAWLER_USER_AGENT");
        if (userAgent != null) {
            config.setUserAgent(userAgent);
        }

        String dbPath = System.getenv("DB_PATH");
        if (dbPath != null) {
            config.setDatabasePath(dbPath);
        }
    }
}
