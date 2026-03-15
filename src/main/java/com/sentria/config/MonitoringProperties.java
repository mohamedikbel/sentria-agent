package com.sentria.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(
        boolean enabled,
        int intervalSeconds,
        List<String> collectors,
        int summaryIntervalSeconds,
        int processIntervalSeconds,
        int verdictIntervalSeconds,
        int verdictReportDays,
        int verdictTopProcesses,
        int retentionDays,
        double batteryFullPercentThreshold,
        double ssdLowHealthPercentThreshold,
        double ssdHighWriteDeltaGbThreshold,
        double storageLowFreeGbThreshold
) {
}
