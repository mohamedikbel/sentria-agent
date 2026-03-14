package com.sentria.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class SsdCollectorService {

    private final SmartctlRunner smartctlRunner;
    private final MetricSnapshotRepository metricSnapshotRepository;
    private final SsdMonitoringStatus ssdMonitoringStatus;

    @Scheduled(fixedRateString = "#{${monitoring.interval-seconds} * 1000}")
    public void collectSsdMetrics() {
        if (!ssdMonitoringStatus.isAvailable()) {
            log.debug("Skipping SSD collection: {}", ssdMonitoringStatus.getReason());
            return;
        }
        log.info("SsdCollectorService running");
        JsonNode root = smartctlRunner.readDeviceJson();

        if (root == null) {
            return;
        }

        collectHealth(root);
        collectBytesWritten(root);
    }

    private void collectHealth(JsonNode root) {
        Double health = extractHealthPercent(root);

        if (health == null) {
            log.info("SSD health percent not found in smartctl JSON");
            return;
        }

        MetricSnapshot snapshot = new MetricSnapshot(
                "local-device",
                MetricType.SSD_HEALTH_PERCENT,
                health,
                Instant.now()
        );

        metricSnapshotRepository.save(snapshot);
        log.info("SSD health saved: {}%", health);
    }

    private void collectBytesWritten(JsonNode root) {
        Double writtenGb = extractBytesWrittenGb(root);

        if (writtenGb == null) {
            log.info("SSD bytes written not found in smartctl JSON");
            return;
        }

        MetricSnapshot snapshot = new MetricSnapshot(
                "local-device",
                MetricType.SSD_BYTES_WRITTEN_GB,
                writtenGb,
                Instant.now()
        );

        metricSnapshotRepository.save(snapshot);
        log.info("SSD bytes written saved: {} GB", writtenGb);
    }

    private Double extractHealthPercent(JsonNode root) {
        JsonNode nvme = root.path("nvme_smart_health_information_log");
        if (nvme.has("percentage_used")) {
            double percentageUsed = nvme.path("percentage_used").asDouble();
            return Math.max(0, 100.0 - percentageUsed);
        }

        JsonNode ata = root.path("ata_smart_attributes").path("table");
        if (ata.isArray()) {
            for (JsonNode attr : ata) {
                String name = attr.path("name").asText("");
                if ("Media_Wearout_Indicator".equalsIgnoreCase(name)
                        || "SSD_Life_Left".equalsIgnoreCase(name)
                        || "Percent_Lifetime_Remain".equalsIgnoreCase(name)) {
                    return attr.path("raw").path("value").asDouble();
                }
            }
        }

        return null;
    }

    private Double extractBytesWrittenGb(JsonNode root) {
        JsonNode nvme = root.path("nvme_smart_health_information_log");

        if (nvme.has("data_units_written")) {
            double dataUnitsWritten = nvme.path("data_units_written").asDouble();
            double bytes = dataUnitsWritten * 512000.0;
            return bytes / 1_000_000_000.0;
        }

        JsonNode ata = root.path("ata_smart_attributes").path("table");
        if (ata.isArray()) {
            for (JsonNode attr : ata) {
                String name = attr.path("name").asText("");
                if ("Total_LBAs_Written".equalsIgnoreCase(name)
                        || "Host_Writes_32MiB".equalsIgnoreCase(name)) {
                    return attr.path("raw").path("value").asDouble();
                }
            }
        }

        return null;
    }
}