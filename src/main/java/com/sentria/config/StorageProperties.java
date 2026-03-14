package com.sentria.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        Smartctl smartctl
) {
    public record Smartctl(
            boolean enabled,
            String command,
            String device
    ) {
    }
}
