package com.sentria.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(
        boolean enabled,
        int intervalSeconds
) {
}
