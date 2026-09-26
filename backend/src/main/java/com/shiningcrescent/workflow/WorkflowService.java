package com.shiningcrescent.workflow;

import com.shiningcrescent.domain.entity.WorkflowEvent;
import com.shiningcrescent.domain.enums.WorkflowEntity;
import com.shiningcrescent.repo.WorkflowEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowEventRepository repo;

    public void step(WorkflowEntity type, String ref, String from, String to, String actor, String comment) {
        repo.save(WorkflowEvent.builder()
                .entityType(type)
                .entityRef(ref)
                .fromStatus(from)
                .toStatus(to)
                .actor(actor)
                .comment(comment)
                .build());
    }

    public List<WorkflowEvent> trail(WorkflowEntity type, String ref) {
        return repo.findByEntityTypeAndEntityRefOrderByOccurredAtAsc(type, ref);
    }

    public List<WorkflowEvent> all() {
        return repo.findAll();
    }

    public WorkflowEvent add(java.util.Map<String, String> body, String actor) {
        WorkflowEvent ev = WorkflowEvent.builder()
                .entityType(WorkflowEntity.valueOf(body.get("entityType")))
                .entityRef(body.get("entityRef"))
                .fromStatus(body.get("fromStatus"))
                .toStatus(body.get("toStatus"))
                .actor(actor)
                .comment(body.getOrDefault("comment", ""))
                .build();
        return repo.save(ev);
    }

    public WorkflowEvent update(Long id, java.util.Map<String, String> body, String actor) {
        WorkflowEvent ev = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Workflow event not found"));
        if (body.get("fromStatus") != null) ev.setFromStatus(body.get("fromStatus"));
        if (body.get("toStatus") != null) ev.setToStatus(body.get("toStatus"));
        if (body.get("comment") != null) ev.setComment(body.get("comment"));
        if (body.get("entityRef") != null) ev.setEntityRef(body.get("entityRef"));
        ev.setActor(actor);
        return repo.save(ev);
    }

    public void delete(Long id, String actor) {
        WorkflowEvent ev = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Workflow event not found"));
        repo.delete(ev);
    }
}
