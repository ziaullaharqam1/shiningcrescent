package com.risingcrescent.event;

public interface EventPublisher {
    void publish(DomainEvent event);
}
