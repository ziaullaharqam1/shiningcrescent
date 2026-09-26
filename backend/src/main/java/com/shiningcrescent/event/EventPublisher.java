package com.shiningcrescent.event;

public interface EventPublisher {
    void publish(DomainEvent event);
}
