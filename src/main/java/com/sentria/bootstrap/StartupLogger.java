package com.sentria.bootstrap;

import com.sentria.application.monitoring.SsdMonitoringStatus;
import com.sentria.config.AppProperties;
import com.sentria.config.MonitoringProperties;
import com.sentria.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class StartupLogger implements CommandLineRunner {

    private final AppProperties appProperties;
    private final MonitoringProperties monitoringProperties;
    private final NotificationProperties notificationProperties;
    private final SsdMonitoringStatus ssdMonitoringStatus;

    @Override
    public void run(String... args) {
        Path setupConfigPath = Path.of("config", "sentria-user.properties").toAbsolutePath();
        Path homeSetupConfigPath = Path.of(System.getProperty("user.home"), ".sentria", "sentria-user.properties").toAbsolutePath();

        log.info("Starting {} on device {}", appProperties.name(), appProperties.deviceName());
        log.info("Guided setup command: java -jar <your-jar>.jar --setup");
        log.info("Expected setup config file: {} (exists={})", setupConfigPath, Files.exists(setupConfigPath));
        log.info("Stable setup config file: {} (exists={})", homeSetupConfigPath, Files.exists(homeSetupConfigPath));
        log.info("MCP server enabled by default. Suggested SSE URL: http://localhost:8080/mcp/sse");
        log.info("MCP message endpoint is announced by SSE (example: /mcp/messages)");
        log.info("Monitoring enabled: {}, interval: {} seconds",
                monitoringProperties.enabled(),
                monitoringProperties.intervalSeconds());
        log.info("Enabled collectors: {}", monitoringProperties.collectors());
        log.info("Global summary interval: {} seconds", monitoringProperties.summaryIntervalSeconds());
        log.info("Process capture interval: {} seconds", monitoringProperties.processIntervalSeconds());
        log.info("Trend verdict interval: {} seconds", monitoringProperties.verdictIntervalSeconds());
        log.info("Data retention: {} day(s)", monitoringProperties.retentionDays());
        log.info("Summary thresholds: battery_full>={}%, ssd_health<={}%, ssd_write_delta>={} GB, storage_free<={} GB",
                monitoringProperties.batteryFullPercentThreshold(),
                monitoringProperties.ssdLowHealthPercentThreshold(),
                monitoringProperties.ssdHighWriteDeltaGbThreshold(),
                monitoringProperties.storageLowFreeGbThreshold());
        log.info("Notification provider: {}", notificationProperties.provider());
        log.info("ntfy enabled: {}, server: {}, topic: {}",
                notificationProperties.ntfy().enabled(),
                notificationProperties.ntfy().serverUrl(),
                notificationProperties.ntfy().topic());
        log.info("SSD monitoring state: available={}, reason={}",
                ssdMonitoringStatus.isAvailable(),
                ssdMonitoringStatus.getReason());
    }
}