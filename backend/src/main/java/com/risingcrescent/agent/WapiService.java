package com.risingcrescent.agent;

import com.risingcrescent.config.AgentProperties;
import com.risingcrescent.config.WapiProperties;
import com.risingcrescent.domain.entity.UserAccount;
import com.risingcrescent.repo.UserAccountRepository;
import com.risingcrescent.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WapiService {
    private final WapiProperties wapi;
    private final AgentProperties agentProps;
    private final UserAccountRepository users;
    private final AgentWorkflowService agent;

    public boolean verifyToken(String mode, String token, String challenge) {
        return "subscribe".equals(mode) && wapi.getVerifyToken().equals(token) && challenge != null;
    }

    @SuppressWarnings("unchecked")
    public void handleWebhook(Map<String, Object> payload) {
        List<Incoming> messages = extract(payload);
        for (Incoming in : messages) {
            try {
                UserAccount user = resolveUser(in.from());
                authenticate(user);
                String text = in.text();
                if ((text == null || text.isBlank()) && in.mediaId() != null) {
                    text = transcribe(in.mediaId());
                }
                if (text == null || text.isBlank()) {
                    sendText(in.from(), "Send a voice note or a message. I can create products, POs, approve or quarantine, run reports, and add to cart.");
                    continue;
                }
                Map<String, Object> reply = agent.chat(user.getUsername(), text, List.of());
                sendText(in.from(), String.valueOf(reply.getOrDefault("message", "Done.")));
            } catch (Exception e) {
                log.warn("WAPI message failed from {}: {}", in.from(), e.getMessage());
                try {
                    sendText(in.from(), "I could not complete that. Sign in on the site if this number is not linked to your account.");
                } catch (Exception ignored) {
                    // ignore send failure
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    public void sendText(String to, String body) {
        if (!wapi.isEnabled() || wapi.getToken() == null || wapi.getToken().isBlank()
                || wapi.getPhoneNumberId() == null || wapi.getPhoneNumberId().isBlank()) {
            log.info("WAPI outbound skipped (not configured): {} -> {}", to, body);
            return;
        }
        String digits = to.replace("+", "");
        RestClient client = RestClient.builder().baseUrl(wapi.getGraphUrl()).build();
        client.post()
                .uri("/{id}/messages", wapi.getPhoneNumberId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + wapi.getToken())
                .body(Map.of(
                        "messaging_product", "whatsapp",
                        "to", digits,
                        "type", "text",
                        "text", Map.of("body", body == null ? "" : body)
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private String transcribe(String mediaId) {
        if (agentProps.getOpenaiKey() == null || agentProps.getOpenaiKey().isBlank()
                || wapi.getToken() == null || wapi.getToken().isBlank()) {
            return "";
        }
        try {
            RestClient graph = RestClient.builder().baseUrl(wapi.getGraphUrl()).build();
            Map<?, ?> meta = graph.get()
                    .uri("/{id}", mediaId)
                    .header("Authorization", "Bearer " + wapi.getToken())
                    .retrieve()
                    .body(Map.class);
            String url = meta == null ? null : String.valueOf(meta.get("url"));
            if (url == null || url.isBlank() || "null".equals(url)) {
                return "";
            }
            byte[] audio = graph.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + wapi.getToken())
                    .retrieve()
                    .body(byte[].class);
            if (audio == null || audio.length == 0) {
                return "";
            }
            // Whisper expects multipart; without a dedicated converter we skip binary upload here
            // and ask the shopper to use in-app voice. Text WhatsApp messages still run the agent.
            log.info("Received WAPI audio {} bytes; use in-app voice or send text", audio.length);
            return "";
        } catch (Exception e) {
            log.warn("WAPI transcribe failed: {}", e.getMessage());
            return "";
        }
    }

    private UserAccount resolveUser(String from) {
        String normalized;
        try {
            normalized = PhoneNumbers.normalize(from);
        } catch (IllegalArgumentException e) {
            normalized = from.startsWith("+") ? from : "+" + from.replaceAll("\\D", "");
        }
        Optional<UserAccount> byPhone = users.findByPhone(normalized);
        if (byPhone.isPresent()) {
            return byPhone.get();
        }
        String digits = normalized.replace("+", "");
        return users.findAll().stream()
                .filter(u -> u.getPhone() != null && u.getPhone().replaceAll("\\D", "").equals(digits))
                .findFirst()
                .or(() -> users.findByUsername(wapi.getDefaultUsername()))
                .orElseThrow(() -> new IllegalArgumentException("No user for this WhatsApp number"));
    }

    private void authenticate(UserAccount user) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
            role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        });
        var auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @SuppressWarnings("unchecked")
    private List<Incoming> extract(Map<String, Object> payload) {
        List<Incoming> out = new ArrayList<>();
        if (payload.get("from") != null && (payload.get("text") != null || payload.get("message") != null || payload.get("body") != null)) {
            out.add(new Incoming(String.valueOf(payload.get("from")),
                    String.valueOf(payload.getOrDefault("text", payload.getOrDefault("message", payload.get("body")))),
                    payload.get("audioId") == null ? null : String.valueOf(payload.get("audioId"))));
            return out;
        }
        Object entry = payload.get("entry");
        if (!(entry instanceof List<?> entries)) {
            return out;
        }
        for (Object e : entries) {
            if (!(e instanceof Map<?, ?> em)) continue;
            Object changes = em.get("changes");
            if (!(changes instanceof List<?> ch)) continue;
            for (Object c : ch) {
                if (!(c instanceof Map<?, ?> cm)) continue;
                Object value = cm.get("value");
                if (!(value instanceof Map<?, ?> vm)) continue;
                Object messages = vm.get("messages");
                if (!(messages instanceof List<?> ms)) continue;
                for (Object m : ms) {
                    if (!(m instanceof Map<?, ?> msg)) continue;
                    String from = String.valueOf(msg.get("from"));
                    Object typeObj = msg.get("type");
                    String type = String.valueOf(typeObj == null ? "text" : typeObj);
                    String text = "";
                    String mediaId = null;
                    if ("text".equals(type) && msg.get("text") instanceof Map<?, ?> tm) {
                        text = String.valueOf(tm.get("body"));
                    } else if ("audio".equals(type) && msg.get("audio") instanceof Map<?, ?> am) {
                        mediaId = String.valueOf(am.get("id"));
                    } else if ("voice".equals(type) && msg.get("voice") instanceof Map<?, ?> vm2) {
                        mediaId = String.valueOf(vm2.get("id"));
                    } else if (msg.get("text") instanceof Map<?, ?> tm) {
                        text = String.valueOf(tm.get("body"));
                    }
                    out.add(new Incoming(from, text, mediaId));
                }
            }
        }
        return out;
    }

    private record Incoming(String from, String text, String mediaId) {}
}
