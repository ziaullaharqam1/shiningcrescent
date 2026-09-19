package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.WorkflowEvent;
import com.risingcrescent.domain.enums.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, Long> {
    List<WorkflowEvent> findByEntityTypeAndEntityRefOrderByOccurredAtAsc(WorkflowEntity entityType, String entityRef);
}
