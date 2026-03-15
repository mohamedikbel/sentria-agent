package com.sentria.application.monitoring;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@DependsOn("flyway")
@Order(0)
@RequiredArgsConstructor
public class SsdStartupProbe implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SsdStartupProbe.class);

    private final MetricCollectionOrchestrator metricCollectionOrchestrator;
    private final SsdMonitoringStatus ssdMonitoringStatus;

    @Override
    public void run(String... args) {
        metricCollectionOrchestrator.collectOnce();
        log.info("SSD startup probe complete: available={}, reason={}",
                ssdMonitoringStatus.isAvailable(),
                ssdMonitoringStatus.getReason());
    }
}




