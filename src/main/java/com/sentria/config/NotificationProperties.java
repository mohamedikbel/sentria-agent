package com.sentria.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(
        String provider,
        Ntfy ntfy
) {
    public record Ntfy(
            boolean enabled,
            String serverUrl,
            String topic
    ) {
    }
}
