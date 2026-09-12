package com.rtap.streaming.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Job configuration resolved from (highest wins): CLI args {@code --key value} →
 * Amazon Managed Flink runtime properties → defaults passed at call sites.
 *
 * <p>Deliberately not Flink's ParameterTool: this class owns the Managed-Flink merge,
 * is trivially serializable, and keeps flink-java off the dependency tree.
 */
public final class JobParams implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(JobParams.class);

    private final Map<String, String> values;

    private JobParams(Map<String, String> values) {
        this.values = values;
    }

    public static JobParams from(String[] args) {
        Map<String, String> map = new LinkedHashMap<>(managedFlinkProperties());
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                continue;
            }
            String key = args[i].substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                map.put(key, args[++i]);
            } else {
                map.put(key, "true"); // bare flag
            }
        }
        return new JobParams(map);
    }

    public static JobParams of(Map<String, String> values) {
        return new JobParams(new LinkedHashMap<>(values));
    }

    /**
     * On Amazon Managed Flink, configuration arrives as runtime property groups
     * (Terraform {@code environment_properties}); everywhere else this is empty.
     * All groups are flattened — keys are globally unique by convention
     * (e.g. {@code kafka.bootstrap.servers}).
     */
    private static Map<String, String> managedFlinkProperties() {
        try {
            Map<String, Properties> groups =
                    com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime.getApplicationProperties();
            if (groups == null || groups.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, String> flat = new LinkedHashMap<>();
            groups.values().forEach(props ->
                    props.stringPropertyNames().forEach(k -> flat.put(k, props.getProperty(k))));
            LOG.info("Loaded {} Managed Flink runtime properties", flat.size());
            return flat;
        } catch (Throwable notOnManagedFlink) {
            return Collections.emptyMap();
        }
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public String require(String key) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required job parameter: --" + key);
        }
        return v;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = values.get(key);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    public long getLong(String key, long defaultValue) {
        String v = values.get(key);
        try {
            return v == null ? defaultValue : Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw invalidNumber(key, v);
        }
    }

    public int getInt(String key, int defaultValue) {
        String v = values.get(key);
        try {
            return v == null ? defaultValue : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw invalidNumber(key, v);
        }
    }

    public double getDouble(String key, double defaultValue) {
        String v = values.get(key);
        try {
            return v == null ? defaultValue : Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw invalidNumber(key, v);
        }
    }

    /** Fail fast at submit time with the parameter NAME, not a bare NumberFormatException. */
    private static IllegalArgumentException invalidNumber(String key, String value) {
        return new IllegalArgumentException(
                "Job parameter --%s must be numeric but was '%s'".formatted(key, value));
    }
}
