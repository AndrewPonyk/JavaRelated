package com.ehrplatform.common.dto;

import java.time.Instant;

/**
 * Canonical envelope for every EHR domain event on Kafka.
 *
 * <p>This is the single shared contract between producers and consumers. Keeping
 * it in {@code common} prevents schema drift across services.
 * Future (P3-5): an Avro schema in a Schema Registry would enable safe evolution.
 *
 * @param eventId     globally unique id — consumers dedupe on this (at-least-once delivery)
 * @param eventType   e.g. {@code ehr.patient.created}, {@code ehr.encounter.updated}
 * @param resourceType FHIR resource type the event concerns (e.g. {@code Patient})
 * @param resourceId  logical id of the affected resource
 * @param sourceFacility originating hospital/clinic identifier
 * @param occurredAt  domain time the change happened
 * @param traceId     distributed-trace correlation id
 * @param payload     JSON body (FHIR resource or delta); opaque to the bus
 */
public record EhrEventEnvelope(
        String eventId,
        String eventType,
        String resourceType,
        String resourceId,
        String sourceFacility,
        Instant occurredAt,
        String traceId,
        String payload) {
}
