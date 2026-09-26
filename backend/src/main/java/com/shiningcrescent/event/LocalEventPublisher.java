package com.shiningcrescent.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class LocalEventPublisher implements EventPublisher {
    private final ApplicationEventPublisher springEvents;

    @Override
    public void publish(DomainEvent event) {
        log.debug("Local event {} {}", event.type(), event.key());
        springEvents.publishEvent(event);
    }
}
