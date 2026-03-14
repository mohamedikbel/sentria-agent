package com.sentria.collector;


import com.fasterxml.jackson.databind.JsonNode;
import com.sentria.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmartctlHealthCheckService implements CommandLineRunner {

    private final StorageProperties storageProperties;
    private final SmartctlRunner smartctlRunner;
    private final SsdMonitoringStatus ssdMonitoringStatus;

    @Override
    public void run(String... args) {
        StorageProperties.Smartctl smartctl = storageProperties.smartctl();

        if (smartctl == null || !smartctl.enabled()) {
            ssdMonitoringStatus.markUnavailable("smartctl disabled in config");
            log.info("SSD monitoring disabled: {}", ssdMonitoringStatus.getReason());
            return;
        }

        JsonNode root = smartctlRunner.readDeviceJson();

        if (root == null || root.isMissingNode() || root.isEmpty()) {
            ssdMonitoringStatus.markUnavailable("smartctl returned no usable data");
            log.warn("SSD monitoring unavailable: {}", ssdMonitoringStatus.getReason());
            return;
        }

        boolean hasNvme = root.has("nvme_smart_health_information_log");
        boolean hasAta = root.path("ata_smart_attributes").has("table");

        if (hasNvme || hasAta) {
            ssdMonitoringStatus.markAvailable("SMART data detected");
            log.info("SSD monitoring available: {}", ssdMonitoringStatus.getReason());
        } else {
            ssdMonitoringStatus.markUnavailable("SMART structure not recognized");
            log.warn("SSD monitoring unavailable: {}", ssdMonitoringStatus.getReason());
        }
    }
}
