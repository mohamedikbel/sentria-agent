package com.sentria.application.monitoring;

import com.sentria.ai.AiTrendVerdictFormatter;
import com.sentria.application.port.BehaviorSessionStore;
import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.application.port.NotificationSender;
import com.sentria.application.port.ProcessSnapshotStore;
import com.sentria.config.AiFormattingProperties;
import com.sentria.config.MonitoringProperties;
import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.domain.ProcessSnapshot;
import com.sentria.notification.FormattedNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class PeriodicMetricVerdictService {

    private final MetricSnapshotStore metricSnapshotStore;
    private final ProcessSnapshotStore processSnapshotStore;
    private final BehaviorSessionStore behaviorSessionStore;
    private final AiTrendVerdictFormatter aiTrendVerdictFormatter;
    private final NotificationSender notificationSender;
    private final MonitoringProperties monitoringProperties;
    private final AiFormattingProperties aiFormattingProperties;
    private final AtomicInteger aiAuthFailureCount = new AtomicInteger(0);
    private volatile Instant aiDisabledUntil = Instant.EPOCH;

    @Scheduled(fixedRateString = "#{${monitoring.verdict-interval-seconds} * 1000}")
    public void sendPeriodicVerdict() {
        int reportDays = Math.max(1, monitoringProperties.verdictReportDays());
        int topProcessLimit = Math.max(1, monitoringProperties.verdictTopProcesses());
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(reportDays));

        WindowVerdict verdict = analyzeWindow(reportDays, since);
        List<ProcessSnapshot> mostUsedProcesses = processSnapshotStore.findMostUsedSince(since, topProcessLimit);
        List<BehaviorInsight> behaviorInsights = buildBehaviorInsights(behaviorSessionStore.findSessionsSince(since), now);

        LongPeriodReport report = new LongPeriodReport(reportDays, verdict, mostUsedProcesses, behaviorInsights);

        String technicalSummary = buildTechnicalSummary(report);
        String fallback = buildFallbackDraft(report);

        String body = fallback;
        if (canUseAiFormatting()) {
            try {
                body = aiTrendVerdictFormatter.format(technicalSummary, fallback);
                aiAuthFailureCount.set(0);
            } catch (Exception e) {
                registerAiFailure(e);
                log.warn("AI trend verdict formatting failed, using fallback", e);
            }
        } else {
            log.info("AI trend formatting skipped: {}", aiFormattingStatus());
        }

        String title = "Sentria Long-Period Report - " + reportDays + " day(s)";
        notificationSender.send(new FormattedNotification(
                title,
                body,
                resolvePriority(verdict)
        ));

        log.info("Periodic trend verdict sent for {} day(s) analysis", reportDays);
    }

    private WindowVerdict analyzeWindow(int days, Instant since) {
        MetricStats cpu = metricStats(MetricType.CPU_USAGE_PERCENT, since);
        MetricStats cpuTemp = metricStats(MetricType.CPU_TEMPERATURE_C, since);
        MetricStats ram = metricStats(MetricType.RAM_USAGE_PERCENT, since);
        MetricStats battery = metricStats(MetricType.BATTERY_PERCENT, since);
        MetricStats batteryRemaining = metricStats(MetricType.BATTERY_TIME_REMAINING_MIN, since);
        MetricStats ssdHealth = metricStats(MetricType.SSD_HEALTH_PERCENT, since);
        MetricStats ssdWrites = metricStats(MetricType.SSD_BYTES_WRITTEN_GB, since);
        MetricStats storageUsed = metricStats(MetricType.STORAGE_USED_PERCENT, since);
        MetricStats storageFree = metricStats(MetricType.STORAGE_FREE_GB, since);
        MetricStats netDown = metricStats(MetricType.NETWORK_DOWNLOAD_MBPS, since);
        MetricStats netUp = metricStats(MetricType.NETWORK_UPLOAD_MBPS, since);

        int riskScore = 0;
        List<String> signals = new ArrayList<>();

        if (cpu.latest >= 85.0) {
            riskScore += 1;
            signals.add("CPU high");
        }
        if (ram.latest >= 90.0) {
            riskScore += 1;
            signals.add("RAM high");
        }
        if (cpuTemp.present && cpuTemp.latest >= 85.0) {
            riskScore += 1;
            signals.add("CPU temperature high");
        }
        if (ssdHealth.present && ssdHealth.latest <= monitoringProperties.ssdLowHealthPercentThreshold()) {
            riskScore += 2;
            signals.add("SSD health low");
        }
        if (ssdWrites.present && ssdWrites.delta >= monitoringProperties.ssdHighWriteDeltaGbThreshold() * days) {
            riskScore += 1;
            signals.add("SSD writes high");
        }
        if (storageFree.present && storageFree.latest <= monitoringProperties.storageLowFreeGbThreshold()) {
            riskScore += 1;
            signals.add("Storage free space low");
        }
        if (netUp.present && netUp.avg >= 5.0) {
            riskScore += 1;
            signals.add("Background upload pressure");
        }
        if (batteryRemaining.present && batteryRemaining.latest <= 30.0) {
            signals.add("Battery autonomy often low");
        }

        String verdict = riskScore >= 3 ? "watch_closely" : (riskScore >= 1 ? "attention" : "stable");

        return new WindowVerdict(days, verdict, riskScore, cpu, cpuTemp, ram, battery, batteryRemaining,
                ssdHealth, ssdWrites, storageUsed, storageFree, netDown, netUp, signals);
    }

    private MetricStats metricStats(MetricType type, Instant since) {
        List<MetricSnapshot> history = metricSnapshotStore.findByTypeSince(type, since);
        if (history.isEmpty()) {
            return MetricStats.empty();
        }

        MetricSnapshot first = history.getFirst();
        MetricSnapshot last = history.getLast();

        double avg = history.stream().mapToDouble(MetricSnapshot::value).average().orElse(last.value());
        double min = history.stream().mapToDouble(MetricSnapshot::value).min().orElse(last.value());
        double max = history.stream().mapToDouble(MetricSnapshot::value).max().orElse(last.value());

        double delta = isCumulativeMetric(type)
                ? computeCumulativeDelta(history)
                : last.value() - first.value();

        return new MetricStats(true, last.value(), avg, min, max, delta, history.size());
    }

    private String buildTechnicalSummary(LongPeriodReport report) {
        List<String> lines = new ArrayList<>();

        WindowVerdict w = report.verdict();
        lines.add("PeriodDays=" + report.days());
        lines.add("Verdict=" + w.verdict());
        lines.add("RiskScore=" + w.riskScore());
        lines.add("CPU.latest=" + format(w.cpu().latest()) + ", avg=" + format(w.cpu().avg()));
        if (w.cpuTemp().present()) {
            lines.add("CPU.temp.latest=" + format(w.cpuTemp().latest()));
        }
        lines.add("RAM.latest=" + format(w.ram().latest()) + ", avg=" + format(w.ram().avg()));
        if (w.battery().present()) {
            lines.add("Battery.latest=" + format(w.battery().latest()));
        }
        if (w.batteryRemaining().present()) {
            lines.add("Battery.remaining.min=" + format(w.batteryRemaining().latest()));
        }
        if (w.ssdHealth().present()) {
            lines.add("SSD.health.latest=" + format(w.ssdHealth().latest()));
        }
        if (w.ssdWrites().present()) {
            lines.add("SSD.writes.delta=" + format(w.ssdWrites().delta()));
        }
        if (w.storageUsed().present()) {
            lines.add("Storage.used.latest=" + format(w.storageUsed().latest()));
        }
        if (w.storageFree().present()) {
            lines.add("Storage.free.latest=" + format(w.storageFree().latest()));
        }
        if (w.netDown().present()) {
            lines.add("Network.down.avg=" + format(w.netDown().avg()));
        }
        if (w.netUp().present()) {
            lines.add("Network.up.avg=" + format(w.netUp().avg()));
        }
        lines.add("Signals=" + String.join("|", w.signals()));

        for (ProcessSnapshot p : report.mostUsedProcesses()) {
            lines.add("TopProcess=" + p.processName()
                    + ", avgCpu=" + format(p.cpuPercent())
                    + ", avgMemMb=" + format(p.memoryMb()));
        }

        for (BehaviorInsight insight : report.behaviorInsights()) {
            lines.add("Behavior=" + insight.type().name()
                    + ", sessions=" + insight.count()
                    + ", totalHours=" + format(insight.totalHours()));
        }

        return String.join("\n", lines);
    }

    private String buildFallbackDraft(LongPeriodReport report) {
        List<String> lines = new ArrayList<>();
        WindowVerdict w = report.verdict();

        lines.add("Global Long-Period Status:");
        lines.add("- Overall verdict: " + globalVerdict(w));
        lines.add("- Analysis period: last " + report.days() + " day(s)");
        lines.add("- Risk score: " + w.riskScore() + " (higher means more attention)");
        lines.add("");

        lines.add("Key Trends:");
        lines.add("- CPU Usage: latest " + format(w.cpu().latest()) + "%, average " + format(w.cpu().avg()) + "%");
        if (w.cpuTemp().present()) {
            lines.add("- CPU Temperature: latest " + format(w.cpuTemp().latest()) + " C");
        }
        lines.add("- RAM Usage: latest " + format(w.ram().latest()) + "%, average " + format(w.ram().avg()) + "%");
        if (w.battery().present()) {
            lines.add("- Battery Level: latest " + format(w.battery().latest()) + "%");
        }
        if (w.batteryRemaining().present()) {
            lines.add("- Battery Time Remaining: " + format(w.batteryRemaining().latest()) + " min");
        }
        if (w.ssdHealth().present()) {
            lines.add("- SSD Health: latest " + format(w.ssdHealth().latest()) + "%");
        }
        if (w.ssdWrites().present()) {
            lines.add("- SSD Data Written (Total): +" + format(w.ssdWrites().delta()) + " GB over period");
        }
        if (w.storageFree().present()) {
            lines.add("- Storage Free Space: " + format(w.storageFree().latest()) + " GB");
        }
        if (w.storageUsed().present()) {
            lines.add("- Storage Usage: " + format(w.storageUsed().latest()) + "%");
        }
        if (w.netDown().present() || w.netUp().present()) {
            lines.add("- Network Traffic: download avg " + format(w.netDown().avg())
                    + " MB/s, upload avg " + format(w.netUp().avg()) + " MB/s");
        }
        if (w.signals().isEmpty()) {
            lines.add("- Signals: no major long-term alert detected.");
        } else {
            lines.add("- Signals: " + String.join(", ", w.signals()));
        }
        lines.add("");

        lines.add("Most Used Applications:");
        if (report.mostUsedProcesses().isEmpty()) {
            lines.add("- No process usage data available in this period.");
        } else {
            for (ProcessSnapshot p : report.mostUsedProcesses()) {
                lines.add("- " + p.processName()
                        + ": avg CPU " + format(p.cpuPercent()) + "%"
                        + ", avg RAM " + format(p.memoryMb()) + " MB");
            }
        }
        lines.add("");

        lines.add("Behavior Insights:");
        if (report.behaviorInsights().isEmpty()) {
            lines.add("- No behavior session detected in this period.");
        } else {
            for (BehaviorInsight insight : report.behaviorInsights()) {
                lines.add("- " + behaviorLabel(insight.type())
                        + ": " + insight.count() + " session(s), "
                        + format(insight.totalHours()) + " hour(s)");
            }
        }
        lines.add("");

        lines.add("Recommendations:");
        lines.addAll(buildVerdictRecommendations(report));

        return String.join("\n", lines);
    }

    private List<String> buildVerdictRecommendations(LongPeriodReport report) {
        List<String> recommendations = new ArrayList<>();
        WindowVerdict verdict = report.verdict();

        if (verdict.ssdHealth().present() && verdict.ssdHealth().latest() <= monitoringProperties.ssdLowHealthPercentThreshold()) {
            recommendations.add("- Back up important files now and plan SSD replacement.");
        }

        if (verdict.ssdWrites().present()
                && verdict.ssdWrites().delta() >= monitoringProperties.ssdHighWriteDeltaGbThreshold() * report.days()) {
            recommendations.add("- Reduce heavy write workloads and keep regular backups enabled.");
        }

        if (verdict.cpu().latest() >= 85.0 || verdict.ram().latest() >= 90.0) {
            recommendations.add("- Review high-consumption processes and close non-essential apps.");
        }

        if (verdict.cpuTemp().present() && verdict.cpuTemp().latest() >= 85.0) {
            recommendations.add("- CPU temperature is high. Clean vents/fans and avoid sustained heavy load.");
        }

        if (verdict.storageFree().present() && verdict.storageFree().latest() <= monitoringProperties.storageLowFreeGbThreshold()) {
            recommendations.add("- Free storage is low. Remove large unused files or move data to external/cloud storage.");
        }

        if (verdict.netUp().present() && verdict.netUp().avg() >= 5.0) {
            recommendations.add("- Continuous upload traffic detected. Verify cloud sync/backup tasks and network usage policies.");
        }

        if (verdict.batteryRemaining().present() && verdict.batteryRemaining().latest() <= 30.0) {
            recommendations.add("- Battery time remaining is frequently low; reduce brightness/background load when unplugged.");
        }

        boolean heavyWriteBehavior = report.behaviorInsights().stream()
                .anyMatch(i -> i.type() == BehaviorSessionType.HEAVY_WRITE_ACTIVITY && i.count() > 0);
        if (heavyWriteBehavior) {
            recommendations.add("- Heavy write behavior was detected repeatedly; avoid unnecessary disk-intensive tasks.");
        }

        boolean frequentIdle = report.behaviorInsights().stream()
                .anyMatch(i -> i.type() == BehaviorSessionType.IDLE && i.totalHours() >= 12.0);
        if (frequentIdle) {
            recommendations.add("- Device stays idle for long periods; enable sleep optimization to save energy.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("- Continue current usage and keep long-period monitoring enabled.");
        }

        return recommendations;
    }

    private List<BehaviorInsight> buildBehaviorInsights(List<BehaviorSession> sessions, Instant now) {
        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<BehaviorSessionType, List<BehaviorSession>> byType = sessions.stream()
                .collect(Collectors.groupingBy(BehaviorSession::sessionType));

        return byType.entrySet().stream()
                .map(entry -> {
                    long count = entry.getValue().size();
                    double totalHours = entry.getValue().stream()
                            .mapToDouble(session -> durationHours(session, now))
                            .sum();
                    return new BehaviorInsight(entry.getKey(), count, totalHours);
                })
                .sorted(Comparator.comparingDouble(BehaviorInsight::totalHours).reversed())
                .toList();
    }

    private double durationHours(BehaviorSession session, Instant now) {
        Instant end = session.endedAt() != null ? session.endedAt() : now;
        Duration duration = Duration.between(session.startedAt(), end);
        return Math.max(0.0, duration.toMinutes() / 60.0);
    }

    private String behaviorLabel(BehaviorSessionType type) {
        return switch (type) {
            case VIDEO_EDITING -> "Video Editing";
            case GAMING -> "Gaming";
            case IDLE -> "Idle Time";
            case HEAVY_WRITE_ACTIVITY -> "Heavy Disk Write Activity";
        };
    }

    private String resolvePriority(WindowVerdict verdict) {
        return verdict.riskScore() >= 3 ? "high" : "default";
    }

    private String globalVerdict(WindowVerdict verdict) {
        if (verdict.riskScore() >= 3) {
            return "watch_closely";
        }
        if (verdict.riskScore() >= 1) {
            return "attention";
        }
        return "stable";
    }

    private boolean canUseAiFormatting() {
        if (!aiFormattingProperties.enabled()) {
            return false;
        }
        return !Instant.now().isBefore(aiDisabledUntil);
    }

    private String aiFormattingStatus() {
        if (!aiFormattingProperties.enabled()) {
            return "disabled-by-config";
        }
        return "cooldown-until-" + aiDisabledUntil;
    }

    private void registerAiFailure(Exception exception) {
        if (!isAiAuthFailure(exception)) {
            return;
        }

        int failures = aiAuthFailureCount.incrementAndGet();
        int threshold = Math.max(1, aiFormattingProperties.maxConsecutiveAuthFailures());
        if (failures < threshold) {
            return;
        }

        int cooldownSeconds = Math.max(60, aiFormattingProperties.cooldownSeconds());
        aiDisabledUntil = Instant.now().plusSeconds(cooldownSeconds);
        aiAuthFailureCount.set(0);
        log.warn("AI trend formatting disabled for {} seconds after repeated auth failures", cooldownSeconds);
    }

    private boolean isAiAuthFailure(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("invalid_api_key")
                        || normalized.contains("incorrect api key")
                        || normalized.contains("401")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static boolean isCumulativeMetric(MetricType type) {
        return type == MetricType.SSD_BYTES_WRITTEN_GB;
    }

    private static double computeCumulativeDelta(List<MetricSnapshot> history) {
        double delta = 0.0;
        double previous = history.getFirst().value();

        for (int i = 1; i < history.size(); i++) {
            double current = history.get(i).value();
            if (current >= previous) {
                delta += current - previous;
            }
            previous = current;
        }

        return delta;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record MetricStats(boolean present, double latest, double avg, double min, double max, double delta, int samples) {
        private static MetricStats empty() {
            return new MetricStats(false, 0, 0, 0, 0, 0, 0);
        }
    }

    private record WindowVerdict(int days, String verdict, int riskScore,
                                 MetricStats cpu, MetricStats cpuTemp, MetricStats ram,
                                 MetricStats battery, MetricStats batteryRemaining,
                                 MetricStats ssdHealth, MetricStats ssdWrites,
                                 MetricStats storageUsed, MetricStats storageFree,
                                 MetricStats netDown, MetricStats netUp,
                                 List<String> signals) {
    }

    private record BehaviorInsight(BehaviorSessionType type, long count, double totalHours) {
    }

    private record LongPeriodReport(int days, WindowVerdict verdict,
                                    List<ProcessSnapshot> mostUsedProcesses,
                                    List<BehaviorInsight> behaviorInsights) {
    }
}




