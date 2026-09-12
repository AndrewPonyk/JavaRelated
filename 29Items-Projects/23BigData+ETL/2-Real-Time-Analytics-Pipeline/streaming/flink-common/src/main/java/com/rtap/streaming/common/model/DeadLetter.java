package com.rtap.streaming.common.model;

import java.io.Serializable;
import java.util.Base64;

/**
 * Error envelope for undeserializable/invalid input, produced to
 * {@code events.raw.dlq.v1} (ARCHITECTURE.md §2.6). Carries everything needed to
 * triage and replay: source coordinates, the error, and the raw payload (base64).
 */
public class DeadLetter implements Serializable {

    private static final long serialVersionUID = 1L;

    private String originalTopic;
    private int partition;
    private long offset;
    private String error;
    private long receivedAt;      // processing time, epoch millis
    private String base64Payload;

    public DeadLetter() {
    }

    public static DeadLetter of(String topic, int partition, long offset, String error, byte[] payload) {
        DeadLetter dl = new DeadLetter();
        dl.originalTopic = topic;
        dl.partition = partition;
        dl.offset = offset;
        dl.error = error == null ? "unknown" : error;
        dl.receivedAt = System.currentTimeMillis();
        dl.base64Payload = payload == null ? "" : Base64.getEncoder().encodeToString(payload);
        return dl;
    }

    public byte[] decodePayload() {
        return Base64.getDecoder().decode(base64Payload == null ? "" : base64Payload);
    }

    public String getOriginalTopic() { return originalTopic; }
    public void setOriginalTopic(String originalTopic) { this.originalTopic = originalTopic; }

    public int getPartition() { return partition; }
    public void setPartition(int partition) { this.partition = partition; }

    public long getOffset() { return offset; }
    public void setOffset(long offset) { this.offset = offset; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public long getReceivedAt() { return receivedAt; }
    public void setReceivedAt(long receivedAt) { this.receivedAt = receivedAt; }

    public String getBase64Payload() { return base64Payload; }
    public void setBase64Payload(String base64Payload) { this.base64Payload = base64Payload; }

    @Override
    public String toString() {
        return "DeadLetter{%s-%d@%d: %s}".formatted(originalTopic, partition, offset, error);
    }
}
