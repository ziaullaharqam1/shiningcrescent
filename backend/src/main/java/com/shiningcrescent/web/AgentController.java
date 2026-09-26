package com.shiningcrescent.web;

import com.shiningcrescent.agent.AgentWorkflowService;
import com.shiningcrescent.agent.SpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentWorkflowService agent;
    private final SpeechService speech;

    @PostMapping("/chat")
    public Map<String, Object> chat(Principal p, @RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", body.getOrDefault("text", "")));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = body.get("history") instanceof List<?> list
                ? (List<Map<String, String>>) list
                : List.of();
        return agent.chat(p.getName(), message, history);
    }

    @PostMapping(value = "/transcribe", consumes = "multipart/form-data")
    public Map<String, String> transcribe(@RequestParam("file") MultipartFile file) {
        return Map.of("text", speech.transcribe(file));
    }
}
