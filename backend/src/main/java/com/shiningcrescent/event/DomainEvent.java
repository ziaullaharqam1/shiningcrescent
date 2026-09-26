package com.shiningcrescent.event;

import java.time.Instant;
import java.util.Map;

public record DomainEvent(String topic, String type, String key, Map<String, Object> payload, Instant at) {
    public static DomainEvent of(String topic, String type, String key, Map<String, Object> payload) {
        return new DomainEvent(topic, type, key, payload, Instant.now());
    }
}
