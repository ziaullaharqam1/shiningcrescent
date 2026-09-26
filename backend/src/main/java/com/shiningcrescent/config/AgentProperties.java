package com.shiningcrescent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {
    private String openaiKey = "";
    private String openaiModel = "gpt-4o-mini";
    private String openaiBaseUrl = "https://api.openai.com/v1";
}
