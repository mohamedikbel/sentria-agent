package com.sentria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.formatting")
public record AiFormattingProperties(
        boolean enabled,
        int maxConsecutiveAuthFailures,
        int cooldownSeconds
) {
}

