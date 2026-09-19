package com.risingcrescent.web;

import com.risingcrescent.agent.WapiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent/wapi")
@RequiredArgsConstructor
public class WapiController {
    private final WapiService wapi;

    @GetMapping("/webhook")
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (wapi.verifyToken(mode, token, challenge)) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
        }
        return ResponseEntity.status(403).body("forbidden");
    }

    @PostMapping("/webhook")
    public Map<String, String> incoming(@RequestBody Map<String, Object> payload) {
        wapi.handleWebhook(payload);
        return Map.of("status", "ok");
    }
}
