package com.risingcrescent.web;

import com.risingcrescent.ops.OpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/console/ops")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class OpsController {
    private final OpsService ops;

    @GetMapping
    public Map<String, Object> snapshot() {
        return ops.snapshot();
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(defaultValue = "200") int limit,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Long after) {
        return ops.logView(limit, q, after);
    }

    @PostMapping("/actions")
    public Map<String, Object> action(@RequestBody Map<String, String> body, Principal p) {
        String action = body == null ? null : body.get("action");
        String profile = body == null ? null : body.get("profile");
        return ops.runAction(action, profile, p.getName());
    }

    @PostMapping("/terminal")
    public Map<String, Object> terminal(@RequestBody Map<String, String> body, Principal p) {
        String command = body == null ? "" : body.getOrDefault("command", "");
        return ops.terminal(command, p.getName());
    }
}
