package com.example.trading.infrastructure.config;

import com.example.trading.domain.DomainValidation;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

public record SimulatorConfig(
        String environment,
        int traderCount,
        Duration priceTimeout,
        Duration simulationTimeout,
        int profitParallelism,
        long randomSeed,
        BigDecimal initialCash,
        long initialPosition,
        BigDecimal maximumPriceDeviationPercent) {

    private static final int MAX_TRADERS = 1_000_000;
    private static final int MAX_PARALLELISM = 1_024;
    private static final Duration MAX_TIMEOUT = Duration.ofHours(1);
    private static final Pattern PROFILE_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,31}");

    public SimulatorConfig {
        environment = validateProfile(environment);
        if (traderCount <= 0 || traderCount > MAX_TRADERS) {
            throw new IllegalArgumentException(
                    "traderCount must be between 1 and " + MAX_TRADERS);
        }
        priceTimeout = requirePositive(priceTimeout, "priceTimeout");
        simulationTimeout = requirePositive(simulationTimeout, "simulationTimeout");
        if (profitParallelism <= 0 || profitParallelism > MAX_PARALLELISM) {
            throw new IllegalArgumentException(
                    "profitParallelism must be between 1 and " + MAX_PARALLELISM);
        }
        initialCash = DomainValidation.money(initialCash, "initialCash", true);
        if (initialPosition < 0 || initialPosition > DomainValidation.MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    "initialPosition must be between zero and "
                            + DomainValidation.MAX_QUANTITY);
        }
        maximumPriceDeviationPercent = DomainValidation.money(
                maximumPriceDeviationPercent, "maximumPriceDeviationPercent", true);
        if (maximumPriceDeviationPercent.signum() < 0
                || maximumPriceDeviationPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "maximumPriceDeviationPercent must be between 0 and 100");
        }
    }

    public static SimulatorConfig fromEnvironment() {
        return load(Path.of("config"), System.getenv());
    }

    public static SimulatorConfig load(Path configDirectory, Map<String, String> environment) {
        Objects.requireNonNull(configDirectory, "configDirectory");
        Objects.requireNonNull(environment, "environment");
        Properties properties = loadClasspathProperties();
        String defaultProfile = properties.getProperty("sim.environment", "dev");
        String profile = validateProfile(environment.getOrDefault("SIM_ENV", defaultProfile));
        properties.putAll(loadProperties(configDirectory.resolve(
                "application-" + profile + ".properties")));
        return new SimulatorConfig(
                profile,
                integer(environment, properties, "SIM_TRADER_COUNT", "sim.trader-count", 10_000),
                Duration.ofMillis(positiveLong(
                        environment, properties, "SIM_PRICE_TIMEOUT_MS", "sim.price-timeout-ms", 2_000L)),
                Duration.ofMillis(positiveLong(
                        environment,
                        properties,
                        "SIM_SIMULATION_TIMEOUT_MS",
                        "sim.simulation-timeout-ms",
                        30_000L)),
                integer(
                        environment,
                        properties,
                        "SIM_PROFIT_PARALLELISM",
                        "sim.profit-parallelism",
                        Math.max(1, Runtime.getRuntime().availableProcessors())),
                number(environment, properties, "SIM_RANDOM_SEED", "sim.random-seed", 42L),
                decimal(environment, properties, "SIM_INITIAL_CASH", "sim.initial-cash", "100000.00"),
                positiveLong(
                        environment,
                        properties,
                        "SIM_INITIAL_POSITION",
                        "sim.initial-position",
                        100L),
                decimal(
                        environment,
                        properties,
                        "SIM_MAX_PRICE_DEVIATION_PERCENT",
                        "sim.max-price-deviation-percent",
                        "25"));
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("could not read configuration " + path, exception);
        }
    }

    private static Properties loadClasspathProperties() {
        Properties properties = new Properties();
        try (InputStream input = SimulatorConfig.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            }
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("could not read classpath configuration", exception);
        }
    }

    private static int integer(
            Map<String, String> environment,
            Properties properties,
            String environmentKey,
            String propertyKey,
            int defaultValue) {
        long value = positiveLong(
                environment, properties, environmentKey, propertyKey, defaultValue);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(environmentKey + " is too large");
        }
        return (int) value;
    }

    private static long positiveLong(
            Map<String, String> environment,
            Properties properties,
            String environmentKey,
            String propertyKey,
            long defaultValue) {
        long value = number(
                environment, properties, environmentKey, propertyKey, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(environmentKey + " must be positive");
        }
        return value;
    }

    private static long number(
            Map<String, String> environment,
            Properties properties,
            String environmentKey,
            String propertyKey,
            long defaultValue) {
        String raw = value(environment, properties, environmentKey, propertyKey);
        if (raw == null) {
            return defaultValue;
        }
        if (raw.length() > 32) {
            throw new IllegalArgumentException(environmentKey + " is too long");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(environmentKey + " must be an integer", exception);
        }
    }

    private static BigDecimal decimal(
            Map<String, String> environment,
            Properties properties,
            String environmentKey,
            String propertyKey,
            String defaultValue) {
        String raw = value(environment, properties, environmentKey, propertyKey);
        if (raw != null && raw.length() > 32) {
            throw new IllegalArgumentException(environmentKey + " is too long");
        }
        try {
            return new BigDecimal(raw == null ? defaultValue : raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(environmentKey + " must be a decimal", exception);
        }
    }

    private static String value(
            Map<String, String> environment,
            Properties properties,
            String environmentKey,
            String propertyKey) {
        String environmentValue = environment.get(environmentKey);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        String propertyValue = properties.getProperty(propertyKey);
        return propertyValue == null || propertyValue.isBlank() ? null : propertyValue.trim();
    }

    private static Duration requirePositive(Duration duration, String field) {
        Duration value = Objects.requireNonNull(duration, field);
        if (value.isZero() || value.isNegative() || value.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    field + " must be positive and no longer than " + MAX_TIMEOUT);
        }
        return value;
    }

    private static String validateProfile(String profile) {
        String normalized = Objects.requireNonNull(profile, "environment").trim();
        if (!PROFILE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "environment must contain 1-32 letters, digits, underscores, or hyphens");
        }
        return normalized;
    }
}
