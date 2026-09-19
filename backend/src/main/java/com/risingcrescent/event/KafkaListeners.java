package com.risingcrescent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.risingcrescent.notify.NotificationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaListeners {
    private final ObjectMapper objectMapper;
    private final NotificationEngine notificationEngine;

    @KafkaListener(topics = {"${app.kafka.topics.notifications}", "${app.kafka.topics.orders}"})
    public void onMessage(String json) {
        try {
            DomainEvent event = objectMapper.readValue(json, DomainEvent.class);
            notificationEngine.handle(event);
        } catch (Exception e) {
            log.warn("Kafka consume error: {}", e.getMessage());
        }
    }
}
