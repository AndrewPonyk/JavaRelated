package com.ehrplatform.events.config;

/**
 * Central registry of Kafka topic names — one source of truth shared by
 * producers and consumers to prevent typos and drift.
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String PATIENT_EVENTS = "ehr.patient.events";
    public static final String ENCOUNTER_EVENTS = "ehr.encounter.events";
    public static final String OBSERVATION_EVENTS = "ehr.observation.events";
    public static final String RISK_SCORED = "ehr.risk.scored";

    /** Dead-letter topic suffix; e.g. {@code ehr.patient.events.DLT}. */
    public static final String DLT_SUFFIX = ".DLT";
}
