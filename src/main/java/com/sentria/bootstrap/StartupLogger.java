package com.sentria.bootstrap;

import com.sentria.collector.SsdMonitoringStatus;
import com.sentria.config.AppProperties;
import com.sentria.config.MonitoringProperties;
import com.sentria.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupLogger implements CommandLineRunner {

    private final AppProperties appProperties;
    private final MonitoringProperties monitoringProperties;
    private final NotificationProperties notificationProperties;
    private final SsdMonitoringStatus ssdMonitoringStatus;

    @Override
    public void run(String... args) {
        log.info("Starting {} on device {}", appProperties.name(), appProperties.deviceName());
        log.info("Monitoring enabled: {}, interval: {} seconds",
                monitoringProperties.enabled(),
                monitoringProperties.intervalSeconds());
        log.info("Notification provider: {}", notificationProperties.provider());
        log.info("ntfy enabled: {}, server: {}, topic: {}",
                notificationProperties.ntfy().enabled(),
                notificationProperties.ntfy().serverUrl(),
                notificationProperties.ntfy().topic());
        log.info("SSD monitoring available: {}, reason: {}",
                ssdMonitoringStatus.isAvailable(),
                ssdMonitoringStatus.getReason());
    }
}