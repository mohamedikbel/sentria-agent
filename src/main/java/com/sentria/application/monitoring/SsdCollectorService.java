package com.sentria.application.monitoring;

import com.sentria.infrastructure.monitoring.OshiDiskHealthProvider;
import com.sentria.infrastructure.monitoring.SmartctlDiskHealthProvider;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class SsdCollectorService implements MetricCollectorPlugin {

    private final StorageProperties storageProperties;
    private final SmartctlDiskHealthProvider smartctlDiskHealthProvider;
    private final OshiDiskHealthProvider oshiDiskHealthProvider;
    private final SsdMonitoringStatus ssdMonitoringStatus;

    @Override
    public String collectorName() {
        return "ssd";
    }

    @Override
    public List<MetricSnapshot> collect(Instant collectedAt) {
        List<DiskHealthSnapshot> snapshots;
        SaveResult saveResult;

        StorageProperties.Smart smart = storageProperties.smart();
        boolean preferSmartctl = smart == null || smart.preferSmartctl();

        if (preferSmartctl && smartctlDiskHealthProvider.mode() == DiskHealthMode.SMART_FULL) {
            snapshots = smartctlDiskHealthProvider.collect();
            if (!snapshots.isEmpty()) {
                saveResult = buildSnapshots(snapshots, collectedAt);
                if (saveResult.savedCount() > 0) {
                    ssdMonitoringStatus.markAvailable("SMART data available via smartctl");
                    log.info("SSD collection saved {} metric(s) via SMART [{}]",
                            saveResult.savedCount(),
                            saveResult.savedValuesSummary());
                    return saveResult.metrics();
                }
                log.warn("SMART provider returned {} snapshot(s) but no persistable SSD metric", snapshots.size());
            }
        }

        snapshots = oshiDiskHealthProvider.collect();
        if (!snapshots.isEmpty()) {
            saveResult = buildSnapshots(snapshots, collectedAt);
            if (saveResult.savedCount() > 0) {
                ssdMonitoringStatus.markAvailable("Basic disk data available via OSHI");
                log.info("SSD collection saved {} metric(s) via OSHI [{}]",
                        saveResult.savedCount(),
                        saveResult.savedValuesSummary());
                return saveResult.metrics();
            }
            log.warn("OSHI provider returned {} snapshot(s) but no persistable SSD metric", snapshots.size());
        }

        ssdMonitoringStatus.markUnavailable("No usable disk data from SMART or OSHI");
        log.warn("SSD monitoring unavailable: {}", ssdMonitoringStatus.getReason());
        return List.of();
    }

    private SaveResult buildSnapshots(List<DiskHealthSnapshot> snapshots, Instant collectedAt) {
        List<MetricSnapshot> metrics = new ArrayList<>();
        List<String> savedValues = new ArrayList<>();

        Double worstHealthPercent = snapshots.stream()
                .map(DiskHealthSnapshot::healthPercent)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);

        List<Double> writtenValues = snapshots.stream()
                .map(DiskHealthSnapshot::bytesWrittenGb)
                .filter(Objects::nonNull)
                .toList();

        Double totalWrittenGb = writtenValues.isEmpty()
                ? null
                : writtenValues.stream().reduce(0.0, Double::sum);

        if (worstHealthPercent != null) {
            metrics.add(new MetricSnapshot(
                    "local-device",
                    MetricType.SSD_HEALTH_PERCENT,
                    worstHealthPercent,
                    collectedAt
            ));
            savedValues.add("health=" + format(worstHealthPercent) + "%");
        }

        if (totalWrittenGb != null && totalWrittenGb > 0.0) {
            metrics.add(new MetricSnapshot(
                    "local-device",
                    MetricType.SSD_BYTES_WRITTEN_GB,
                    totalWrittenGb,
                    collectedAt
            ));
            savedValues.add("written_total=" + format(totalWrittenGb) + " GB");
        }

        String summary = savedValues.isEmpty() ? "none" : String.join(", ", savedValues);
        return new SaveResult(metrics.size(), summary, metrics);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record SaveResult(int savedCount, String savedValuesSummary, List<MetricSnapshot> metrics) {
    }
}


