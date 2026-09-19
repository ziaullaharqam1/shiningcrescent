package com.risingcrescent.audit;

import com.risingcrescent.domain.entity.AuditLog;
import com.risingcrescent.event.DomainEvent;
import com.risingcrescent.event.EventPublisher;
import com.risingcrescent.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo;
    private final EventPublisher events;

    public void record(String actor, String action, String entityType, String entityId, String details) {
        AuditLog log = AuditLog.builder()
                .actor(actor)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .build();
        repo.save(log);
        events.publish(DomainEvent.of("rc.audit", action, entityId,
                Map.of("actor", actor == null ? "system" : actor, "entity", entityType, "details", details == null ? "" : details)));
    }

    public List<AuditLog> recent() {
        return repo.findTop200ByOrderByOccurredAtDesc();
    }
}
