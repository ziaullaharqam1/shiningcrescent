package com.shiningcrescent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher springEvents;

    @Override
    public void publish(DomainEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(event.topic(), event.key(), json);
        } catch (Exception e) {
            log.warn("Kafka publish failed, using in-process event: {}", e.getMessage());
            springEvents.publishEvent(event);
        }
    }
}
