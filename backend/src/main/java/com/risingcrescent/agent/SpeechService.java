package com.risingcrescent.agent;

import com.risingcrescent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechService {
    private final AgentProperties props;

    public String transcribe(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No audio was recorded. Hold the mic and speak, then tap it again.");
        }
        if (props.getOpenaiKey() == null || props.getOpenaiKey().isBlank()) {
            throw new IllegalArgumentException("Live voice works in Chrome or Edge. Allow the microphone, tap the mic, speak, then tap it again to convert.");
        }
        try {
            String name = file.getOriginalFilename();
            if (name == null || name.isBlank()) {
                name = "speech.webm";
            }
            String filename = name;
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("model", "whisper-1");
            body.part("language", "en");
            body.part("response_format", "json");
            body.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).contentType(MediaType.parseMediaType(file.getContentType() == null ? "audio/webm" : file.getContentType()));

            RestClient client = RestClient.builder().baseUrl(props.getOpenaiBaseUrl()).build();
            Map<?, ?> resp = client.post()
                    .uri("/audio/transcriptions")
                    .header("Authorization", "Bearer " + props.getOpenaiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(Map.class);
            Object raw = resp == null ? null : resp.get("text");
            String text = raw == null ? "" : String.valueOf(raw).trim();
            if (text.isBlank() || "null".equals(text)) {
                throw new IllegalArgumentException("I could not hear words in that recording. Try again closer to the mic.");
            }
            return text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Transcription failed: {}", e.getMessage());
            throw new IllegalArgumentException("Voice could not be converted to text. Try Chrome or Edge, or type the request.");
        }
    }
}
