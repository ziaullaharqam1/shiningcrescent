package com.shiningcrescent.domain.entity;

import com.shiningcrescent.domain.enums.WorkflowEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "workflow_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private WorkflowEntity entityType;

    private String entityRef;
    private String fromStatus;
    private String toStatus;
    private String actor;
    @Column(length = 1000)
    private String comment;
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        occurredAt = Instant.now();
    }
}
