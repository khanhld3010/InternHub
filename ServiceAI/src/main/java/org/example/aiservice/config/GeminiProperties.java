package org.example.aiservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private String apiKey = "mock-key";
    private String model = "gemini-1.5-flash";
    private boolean mockMode = true;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 30;
}
