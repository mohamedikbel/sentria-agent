package com.sentria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProviderProperties(
        boolean enabled,
        String provider,
        String model,
        int timeoutSeconds,
        String openaiApiKey,
        String openrouterApiKey,
        String groqApiKey,
        String customApiKey,
        String customBaseUrl
) {
}

