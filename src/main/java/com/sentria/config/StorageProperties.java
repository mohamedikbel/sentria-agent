package com.sentria.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        Smart smart
) {
    public record Smart(
            boolean enabled,
            String binaryPath,
            boolean discoveryEnabled,
            boolean preferSmartctl
    ) {
    }
}
