package com.sentria.mcp;

import com.sentria.application.monitoring.SsdMonitoringStatus;
import com.sentria.application.port.FindingStore;
import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.application.port.ProcessSnapshotStore;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.domain.ProcessSnapshot;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class SentriaMonitoringTools {

    private final MetricSnapshotStore metricSnapshotStore;
    private final ProcessSnapshotStore processSnapshotStore;
    private final FindingStore findingStore;
    private final SsdMonitoringStatus ssdMonitoringStatus;

    public SentriaMonitoringTools(MetricSnapshotStore metricSnapshotStore,
                                  ProcessSnapshotStore processSnapshotStore,
                                  FindingStore findingStore,
                                  SsdMonitoringStatus ssdMonitoringStatus) {
        this.metricSnapshotStore = metricSnapshotStore;
        this.processSnapshotStore = processSnapshotStore;
        this.findingStore = findingStore;
        this.ssdMonitoringStatus = ssdMonitoringStatus;
    }

    @Tool(description = "Return a compact global status snapshot for CPU, RAM, battery, storage, network and SSD monitoring health.")
    public SystemStatusResponse sentriaGetSystemStatus() {
        List<MetricValue> metrics = new ArrayList<>();
        addLatest(metrics, MetricType.CPU_USAGE_PERCENT);
        addLatest(metrics, MetricType.CPU_TEMPERATURE_C);
        addLatest(metrics, MetricType.RAM_USAGE_PERCENT);
        addLatest(metrics, MetricType.BATTERY_PERCENT);
        addLatest(metrics, MetricType.BATTERY_CHARGING);
        addLatest(metrics, MetricType.BATTERY_TIME_REMAINING_MIN);
        addLatest(metrics, MetricType.STORAGE_USED_PERCENT);
        addLatest(metrics, MetricType.STORAGE_FREE_GB);
        addLatest(metrics, MetricType.NETWORK_DOWNLOAD_MBPS);
        addLatest(metrics, MetricType.NETWORK_UPLOAD_MBPS);
        addLatest(metrics, MetricType.SSD_HEALTH_PERCENT);
        addLatest(metrics, MetricType.SSD_BYTES_WRITTEN_GB);

        return new SystemStatusResponse(
                Instant.now(),
                ssdMonitoringStatus.isAvailable(),
                ssdMonitoringStatus.getReason(),
                metrics
        );
    }

    @Tool(description = "Return metric trends for the last N minutes. Use minutes between 1 and 1440.")
    public MetricsWindowResponse sentriaGetMetricsWindow(int minutes) {
        int safeMinutes = Math.max(1, Math.min(1440, minutes));
        Instant since = Instant.now().minus(Duration.ofMinutes(safeMinutes));

        List<MetricWindowStat> stats = new ArrayList<>();
        for (MetricType type : MetricType.values()) {
            List<MetricSnapshot> history = metricSnapshotStore.findByTypeSince(type, since);
            if (history.isEmpty()) {
                continue;
            }

            MetricSnapshot first = history.getFirst();
            MetricSnapshot last = history.getLast();
            double avg = history.stream().mapToDouble(MetricSnapshot::value).average().orElse(last.value());
            double min = history.stream().mapToDouble(MetricSnapshot::value).min().orElse(last.value());
            double max = history.stream().mapToDouble(MetricSnapshot::value).max().orElse(last.value());
            double delta = last.value() - first.value();

            stats.add(new MetricWindowStat(
                    type.name(),
                    displayName(type),
                    formatValue(type, last.value()),
                    formatValue(type, avg),
                    formatValue(type, min),
                    formatValue(type, max),
                    formatValue(type, delta),
                    history.size()
            ));
        }

        stats.sort(Comparator.comparing(MetricWindowStat::metricName));
        return new MetricsWindowResponse(safeMinutes, since, Instant.now(), stats);
    }

    @Tool(description = "Return most used processes over the selected time window in minutes.")
    public ProcessPressureResponse sentriaGetTopProcesses(int minutes, int limit) {
        int safeMinutes = Math.max(1, Math.min(1440, minutes));
        int safeLimit = Math.max(1, Math.min(20, limit));
        Instant since = Instant.now().minus(Duration.ofMinutes(safeMinutes));

        List<ProcessSnapshot> top = processSnapshotStore.findMostUsedSince(since, safeLimit);
        List<ProcessUsage> items = top.stream()
                .map(p -> new ProcessUsage(
                        p.processName(),
                        p.pid(),
                        format(p.cpuPercent()) + "%",
                        format(p.memoryMb()) + " MB"
                ))
                .toList();

        return new ProcessPressureResponse(safeMinutes, safeLimit, items);
    }

    @Tool(description = "Return recent findings created in the last N hours.")
    public FindingsResponse sentriaGetRecentFindings(int hours, int limit) {
        int safeHours = Math.max(1, Math.min(24 * 30, hours));
        int safeLimit = Math.max(1, Math.min(50, limit));
        Instant since = Instant.now().minus(Duration.ofHours(safeHours));

        List<FindingItem> findings = findingStore.findRecentSince(since, safeLimit).stream()
                .map(f -> new FindingItem(
                        f.id(),
                        f.type(),
                        f.severity().name(),
                        f.confidence().name(),
                        f.facts(),
                        f.recommendations(),
                        f.createdAt()
                ))
                .toList();

        return new FindingsResponse(safeHours, safeLimit, findings.size(), findings);
    }

    @Tool(description = "Return storage and SSD focused health view for decision making.")
    public StorageSsdResponse sentriaGetStorageSsdHealth() {
        MetricSnapshot storageFree = metricSnapshotStore.findLatestByType(MetricType.STORAGE_FREE_GB);
        MetricSnapshot storageUsed = metricSnapshotStore.findLatestByType(MetricType.STORAGE_USED_PERCENT);
        MetricSnapshot ssdHealth = metricSnapshotStore.findLatestByType(MetricType.SSD_HEALTH_PERCENT);
        MetricSnapshot ssdWritten = metricSnapshotStore.findLatestByType(MetricType.SSD_BYTES_WRITTEN_GB);

        return new StorageSsdResponse(
                storageFree != null ? format(storageFree.value()) + " GB" : "n/a",
                storageUsed != null ? format(storageUsed.value()) + "%" : "n/a",
                ssdHealth != null ? format(ssdHealth.value()) + "%" : "n/a",
                ssdWritten != null ? format(ssdWritten.value()) + " GB" : "n/a",
                ssdMonitoringStatus.isAvailable(),
                ssdMonitoringStatus.getReason()
        );
    }

    private void addLatest(List<MetricValue> metrics, MetricType type) {
        MetricSnapshot snapshot = metricSnapshotStore.findLatestByType(type);
        if (snapshot == null) {
            return;
        }

        metrics.add(new MetricValue(type.name(), displayName(type), formatValue(type, snapshot.value()), snapshot.capturedAt()));
    }

    private String formatValue(MetricType type, double value) {
        return switch (type) {
            case CPU_USAGE_PERCENT, RAM_USAGE_PERCENT, BATTERY_PERCENT, SSD_HEALTH_PERCENT, STORAGE_USED_PERCENT -> format(value) + "%";
            case CPU_TEMPERATURE_C -> format(value) + " C";
            case SSD_BYTES_WRITTEN_GB, STORAGE_FREE_GB -> format(value) + " GB";
            case NETWORK_DOWNLOAD_MBPS, NETWORK_UPLOAD_MBPS -> format(value) + " MB/s";
            case BATTERY_TIME_REMAINING_MIN -> format(value) + " min";
            case BATTERY_CHARGING -> value >= 0.5 ? "on" : "off";
        };
    }

    private String displayName(MetricType type) {
        return switch (type) {
            case CPU_USAGE_PERCENT -> "CPU Usage";
            case CPU_TEMPERATURE_C -> "CPU Temperature";
            case RAM_USAGE_PERCENT -> "RAM Usage";
            case BATTERY_PERCENT -> "Battery Level";
            case BATTERY_CHARGING -> "Battery Charging State";
            case BATTERY_TIME_REMAINING_MIN -> "Battery Time Remaining";
            case SSD_HEALTH_PERCENT -> "SSD Health";
            case SSD_BYTES_WRITTEN_GB -> "SSD Data Written";
            case STORAGE_USED_PERCENT -> "Storage Usage";
            case STORAGE_FREE_GB -> "Storage Free Space";
            case NETWORK_DOWNLOAD_MBPS -> "Network Download";
            case NETWORK_UPLOAD_MBPS -> "Network Upload";
        };
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public record SystemStatusResponse(Instant capturedAt,
                                       boolean ssdMonitoringAvailable,
                                       String ssdMonitoringReason,
                                       List<MetricValue> metrics) {
    }

    public record MetricValue(String metricKey, String metricName, String value, Instant capturedAt) {
    }

    public record MetricsWindowResponse(int windowMinutes,
                                        Instant since,
                                        Instant until,
                                        List<MetricWindowStat> metrics) {
    }

    public record MetricWindowStat(String metricKey,
                                   String metricName,
                                   String latest,
                                   String avg,
                                   String min,
                                   String max,
                                   String delta,
                                   int samples) {
    }

    public record ProcessPressureResponse(int windowMinutes, int limit, List<ProcessUsage> processes) {
    }

    public record ProcessUsage(String name, int pid, String avgCpuPercent, String avgMemoryMb) {
    }

    public record FindingsResponse(int hours, int limit, int count, List<FindingItem> findings) {
    }

    public record FindingItem(String id,
                              String type,
                              String severity,
                              String confidence,
                              List<String> facts,
                              List<String> recommendations,
                              Instant createdAt) {
    }

    public record StorageSsdResponse(String storageFreeGb,
                                     String storageUsedPercent,
                                     String ssdHealthPercent,
                                     String ssdWrittenGb,
                                     boolean ssdMonitoringAvailable,
                                     String ssdMonitoringReason) {
    }
}


