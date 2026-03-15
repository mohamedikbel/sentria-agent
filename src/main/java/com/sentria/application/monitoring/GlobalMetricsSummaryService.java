package com.sentria.application.monitoring;

import com.sentria.ai.AiMetricsSummaryFormatter;
import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.application.port.NotificationSender;
import com.sentria.config.AiFormattingProperties;
import com.sentria.config.MonitoringProperties;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.notification.FormattedNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class GlobalMetricsSummaryService {

	private final MetricSnapshotStore metricSnapshotStore;
	private final AiMetricsSummaryFormatter aiMetricsSummaryFormatter;
	private final NotificationSender notificationSender;
	private final MonitoringProperties monitoringProperties;
	private final SsdMonitoringStatus ssdMonitoringStatus;
	private final AiFormattingProperties aiFormattingProperties;
	private final AtomicInteger aiAuthFailureCount = new AtomicInteger(0);
	private volatile Instant aiDisabledUntil = Instant.EPOCH;

	@Scheduled(fixedRateString = "#{${monitoring.summary-interval-seconds} * 1000}")
	public void sendGlobalSummary() {
		final int lookbackSeconds = Math.max(1, monitoringProperties.summaryIntervalSeconds());
		Instant since = Instant.now().minusSeconds(lookbackSeconds);

		SummaryContext summaryContext = buildSummaryContext(since, lookbackSeconds);
		String rawSummary = summaryContext.rawSummary();
		if (rawSummary.isBlank()) {
			String noDataBody = buildNoDataFallback(lookbackSeconds);
			notificationSender.send(new FormattedNotification(
					"Sentria Global State",
					noDataBody,
					"default"
			));
			log.info("Global summary sent with no recent metrics (window={}s)", lookbackSeconds);
			return;
		}

		String fallbackBody = buildUserFriendlyFallback(summaryContext, lookbackSeconds);
		String body = fallbackBody;
		if (canUseAiFormatting()) {
			try {
				body = aiMetricsSummaryFormatter.format(rawSummary, fallbackBody);
				aiAuthFailureCount.set(0);
			} catch (Exception e) {
				registerAiFailure(e);
				// Keep delivery reliable even when AI formatting fails.
				log.warn("AI summary formatting failed, using raw summary", e);
			}
		} else {
			log.info("AI summary formatting skipped: {}", aiFormattingStatus());
		}

		// Hard safety rules: always include critical recommendations when conditions are true.
		body = enforceCriticalRecommendations(summaryContext, body);

		notificationSender.send(new FormattedNotification(
				"Sentria Global State",
				body,
				summaryContext.priority()
		));

		log.info("Global metrics summary sent (window={}s, priority={})", lookbackSeconds, summaryContext.priority());
	}

	private String buildNoDataFallback(int lookbackSeconds) {
		return String.join("\n",
				"Overall Status:",
				"- System state: Collecting data",
				"",
				"Context:",
				"- Report window: last " + format(lookbackSeconds / 60.0) + " minute(s)",
				"- SSD monitoring: " + (ssdMonitoringStatus.isAvailable() ? "Available" : "Limited"),
				"- Storage: " + buildStorageSummary(),
				"",
				"Key Metrics:",
				"- Waiting for enough recent samples to build a full summary.",
				"",
				"Signals:",
				"- No reliable signal yet for this window.",
				"",
				"Recommendations:",
				"- Keep the agent running; the next cycle will include full metrics."
		);
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
		log.warn("AI summary formatting disabled for {} seconds after repeated auth failures", cooldownSeconds);
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

	private SummaryContext buildSummaryContext(Instant since, int lookbackSeconds) {
		List<MetricStats> stats = new ArrayList<>();
		for (MetricType type : MetricType.values()) {
			List<MetricSnapshot> history = metricSnapshotStore.findByTypeSince(type, since);
			if (history.isEmpty()) {
				continue;
			}
			stats.add(MetricStats.from(type, history));
		}

		if (stats.isEmpty()) {
			return new SummaryContext("", "default", List.of(), List.of());
		}

		List<String> highlights = buildHighlights(stats);
		String priority = resolvePriority(stats);
		String rawSummary = buildStructuredSummary(stats, highlights, lookbackSeconds);
		return new SummaryContext(rawSummary, priority, highlights, stats);
	}

	private String buildUserFriendlyFallback(SummaryContext summaryContext, int lookbackSeconds) {
		List<String> lines = new ArrayList<>();
		double windowMinutes = lookbackSeconds / 60.0;

		lines.add("Overall Status:");
		lines.add("- System state: " + ("high".equals(summaryContext.priority())
				? "Attention needed"
				: "Stable"));
		lines.add("");

		lines.add("Context:");
		lines.add("- Report window: last " + format(windowMinutes) + " minute(s)");
		lines.add("- SSD monitoring: " + (ssdMonitoringStatus.isAvailable() ? "Available" : "Limited"));
		lines.add("- Storage: " + buildStorageSummary());
		if (ssdMonitoringStatus.getReason() != null && !ssdMonitoringStatus.getReason().isBlank()) {
			lines.add("- SSD source: " + ssdMonitoringStatus.getReason());
		}
		lines.add("");

		lines.add("Key Metrics:");
		for (MetricStats stat : summaryContext.stats()) {
			lines.add("- " + displayName(stat.type())
					+ ": " + formatWithUnit(stat.type(), stat.latest())
					+ " (window avg " + formatWithUnit(stat.type(), stat.avg())
					+ ", change " + formatDelta(stat.type(), stat.delta())
					+ ", trend " + stat.trend() + ")");
		}
		lines.add("");

		lines.add("Signals:");
		if (summaryContext.highlights().isEmpty()) {
			lines.add("- No critical signal detected in the recent window.");
		} else {
			summaryContext.highlights().forEach(h -> lines.add("- " + h));
		}
		lines.add("");

		lines.add("Recommendations:");
		List<String> recommendations = buildRecommendations(summaryContext.stats());
		recommendations.forEach(r -> lines.add("- " + r));

		return String.join("\n", lines);
	}

	private List<String> buildRecommendations(List<MetricStats> stats) {
		List<String> recommendations = new ArrayList<>();

		metric(stats, MetricType.CPU_USAGE_PERCENT)
				.filter(s -> s.latest() >= 85.0)
				.ifPresent(s -> recommendations.add("Reduce heavy background applications to lower CPU load."));

		metric(stats, MetricType.RAM_USAGE_PERCENT)
				.filter(s -> s.latest() >= 90.0)
				.ifPresent(s -> recommendations.add("Close non-essential applications to free memory."));

		metric(stats, MetricType.CPU_TEMPERATURE_C)
				.filter(s -> s.latest() >= 85.0)
				.ifPresent(s -> recommendations.add("Device temperature is high. Improve ventilation and reduce heavy workloads."));

		if (isBatteryFullAndCharging(stats)) {
			recommendations.add(buildBatteryUnplugRecommendation(stats));
		} else if (isBatteryNearFull(stats)) {
			recommendations.add(buildBatteryFullConditionalRecommendation(stats));
		}

		metric(stats, MetricType.SSD_HEALTH_PERCENT)
				.filter(s -> s.latest() <= monitoringProperties.ssdLowHealthPercentThreshold())
				.ifPresent(s -> recommendations.add("Back up important data and plan SSD replacement."));

		metric(stats, MetricType.SSD_BYTES_WRITTEN_GB)
				.filter(s -> s.delta() >= monitoringProperties.ssdHighWriteDeltaGbThreshold())
				.ifPresent(s -> recommendations.add("Limit intensive disk writes in the next period."));

		metric(stats, MetricType.NETWORK_UPLOAD_MBPS)
				.filter(s -> s.avg() >= 2.0)
				.ifPresent(s -> recommendations.add("Background uploads are high. Check sync/backup tools if this is unexpected."));

		metric(stats, MetricType.BATTERY_TIME_REMAINING_MIN)
				.filter(s -> s.latest() <= 30.0)
				.ifPresent(s -> recommendations.add("Battery autonomy is low. Save work and plug in the charger soon."));

		storageStats().filter(this::isStorageLow)
				.ifPresent(s -> recommendations.add(buildStorageCleanupRecommendation(s)));

		if (recommendations.isEmpty()) {
			recommendations.add("No urgent action needed right now. Keep regular monitoring enabled.");
		}

		return recommendations;
	}

	private String buildStructuredSummary(List<MetricStats> stats, List<String> highlights, int lookbackSeconds) {
		List<String> lines = new ArrayList<>();

		lines.add("## Global Monitoring Snapshot");
		lines.add("Window Seconds: " + lookbackSeconds);
		lines.add("SSD Monitoring: " + (ssdMonitoringStatus.isAvailable() ? "available" : "unavailable"));
		lines.add("SSD Reason: " + ssdMonitoringStatus.getReason());
		lines.add("Battery Full And Charging: " + (isBatteryFullAndCharging(stats) ? "true" : "false"));
		lines.add("Battery Near Full: " + (isBatteryNearFull(stats) ? "true" : "false"));
		lines.add("Storage Summary: " + buildStorageSummary());

		lines.add("## Highlights");
		if (highlights.isEmpty()) {
			lines.add("- No immediate anomaly detected from recent samples.");
		} else {
			highlights.forEach(h -> lines.add("- " + h));
		}

		lines.add("## Metrics");
		for (MetricStats stat : stats) {
			lines.add(displayName(stat.type())
					+ ": latest=" + formatWithUnit(stat.type(), stat.latest())
					+ ", avg=" + formatWithUnit(stat.type(), stat.avg())
					+ ", min=" + formatWithUnit(stat.type(), stat.min())
					+ ", max=" + formatWithUnit(stat.type(), stat.max())
					+ ", delta=" + formatDelta(stat.type(), stat.delta())
					+ ", trend=" + stat.trend()
					+ ", samples=" + stat.samples());
		}

		return String.join("\n", lines);
	}

	private List<String> buildHighlights(List<MetricStats> stats) {
		List<String> highlights = new ArrayList<>();

		metric(stats, MetricType.CPU_USAGE_PERCENT)
				.filter(s -> s.latest() >= 85.0)
				.ifPresent(s -> highlights.add("CPU usage is high (" + format(s.latest()) + "%)."));

		metric(stats, MetricType.RAM_USAGE_PERCENT)
				.filter(s -> s.latest() >= 90.0)
				.ifPresent(s -> highlights.add("RAM usage is high (" + format(s.latest()) + "%)."));

		metric(stats, MetricType.CPU_TEMPERATURE_C)
				.filter(s -> s.latest() >= 85.0)
				.ifPresent(s -> highlights.add("CPU temperature is high (" + format(s.latest()) + " C)."));

		metric(stats, MetricType.BATTERY_PERCENT)
				.flatMap(b -> metric(stats, MetricType.BATTERY_CHARGING)
						.filter(c -> c.latest() >= 0.5 && b.latest() >= monitoringProperties.batteryFullPercentThreshold())
						.map(c -> b))
				.ifPresent(b -> highlights.add("Battery is full and still charging."));

		metric(stats, MetricType.SSD_HEALTH_PERCENT)
				.filter(s -> s.latest() <= monitoringProperties.ssdLowHealthPercentThreshold())
				.ifPresent(s -> highlights.add("SSD health is low (" + format(s.latest()) + "%)."));

		metric(stats, MetricType.SSD_BYTES_WRITTEN_GB)
				.filter(s -> s.delta() >= monitoringProperties.ssdHighWriteDeltaGbThreshold())
				.ifPresent(s -> highlights.add("High SSD write activity detected (+" + format(s.delta()) + " GB)."));

		metric(stats, MetricType.NETWORK_DOWNLOAD_MBPS)
				.filter(s -> s.avg() >= 5.0)
				.ifPresent(s -> highlights.add("Sustained network download traffic detected (avg " + format(s.avg()) + " MB/s)."));

		metric(stats, MetricType.BATTERY_TIME_REMAINING_MIN)
				.filter(s -> s.latest() <= 30.0)
				.ifPresent(s -> highlights.add("Battery remaining time is low (" + format(s.latest()) + " min)."));

		storageStats().filter(this::isStorageLow)
				.ifPresent(s -> highlights.add("Low free storage space detected (" + format(s.freeGb()) + " GB free)."));

		return highlights;
	}

	private Optional<MetricStats> metric(List<MetricStats> stats, MetricType type) {
		return stats.stream().filter(s -> s.type() == type).findFirst();
	}

	private String resolvePriority(List<MetricStats> stats) {
		boolean hasCriticalSignal = metric(stats, MetricType.SSD_HEALTH_PERCENT)
				.filter(s -> s.latest() <= monitoringProperties.ssdLowHealthPercentThreshold())
				.isPresent();

		boolean hasHighSignal = metric(stats, MetricType.CPU_USAGE_PERCENT)
				.filter(s -> s.latest() >= 90.0)
				.isPresent()
				|| metric(stats, MetricType.RAM_USAGE_PERCENT)
				.filter(s -> s.latest() >= 95.0)
				.isPresent();

		if (hasCriticalSignal || hasHighSignal) {
			return "high";
		}

		return "default";
	}

	private String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private String formatWithUnit(MetricType type, double value) {
		return switch (type) {
			case CPU_USAGE_PERCENT, RAM_USAGE_PERCENT, BATTERY_PERCENT, SSD_HEALTH_PERCENT, STORAGE_USED_PERCENT -> format(value) + "%";
			case SSD_BYTES_WRITTEN_GB -> format(value) + " GB";
			case BATTERY_CHARGING -> value >= 0.5 ? "on" : "off";
			case CPU_TEMPERATURE_C -> format(value) + " C";
			case BATTERY_TIME_REMAINING_MIN -> format(value) + " min";
			case STORAGE_FREE_GB -> format(value) + " GB";
			case NETWORK_DOWNLOAD_MBPS, NETWORK_UPLOAD_MBPS -> format(value) + " MB/s";
			default -> format(value);
		};
	}

	private String formatDelta(MetricType type, double delta) {
		if (type == MetricType.BATTERY_CHARGING) {
			return delta == 0.0 ? "no change" : "state changed";
		}

		if (type == MetricType.SSD_BYTES_WRITTEN_GB) {
			return "+" + format(Math.max(0.0, delta)) + " GB";
		}

		String sign = delta > 0 ? "+" : "";
		return sign + formatWithUnit(type, delta);
	}

	private String displayName(MetricType type) {
		return switch (type) {
			case CPU_USAGE_PERCENT -> "CPU Usage";
			case RAM_USAGE_PERCENT -> "RAM Usage";
			case BATTERY_PERCENT -> "Battery Level";
			case BATTERY_CHARGING -> "Battery Charging State";
			case BATTERY_TIME_REMAINING_MIN -> "Battery Time Remaining";
			case SSD_HEALTH_PERCENT -> "SSD Health";
			case SSD_BYTES_WRITTEN_GB -> "SSD Data Written (Total)";
			case CPU_TEMPERATURE_C -> "CPU Temperature";
			case STORAGE_USED_PERCENT -> "Storage Usage";
			case STORAGE_FREE_GB -> "Storage Free Space";
			case NETWORK_DOWNLOAD_MBPS -> "Network Download";
			case NETWORK_UPLOAD_MBPS -> "Network Upload";
		};
	}

	private boolean isBatteryFullAndCharging(List<MetricStats> stats) {
		Optional<MetricStats> batteryLevel = metric(stats, MetricType.BATTERY_PERCENT);
		Optional<MetricStats> charging = metric(stats, MetricType.BATTERY_CHARGING);

		return batteryLevel.filter(b -> b.latest() >= monitoringProperties.batteryFullPercentThreshold()).isPresent()
				&& charging.filter(c -> c.latest() >= 0.5).isPresent();
	}

	private boolean isBatteryNearFull(List<MetricStats> stats) {
		return metric(stats, MetricType.BATTERY_PERCENT)
				.filter(b -> b.latest() >= monitoringProperties.batteryFullPercentThreshold())
				.isPresent();
	}

	private String enforceCriticalRecommendations(SummaryContext summaryContext, String body) {
		String result = body;
		String normalized = body.toLowerCase(Locale.ROOT);

		if (isBatteryFullAndCharging(summaryContext.stats()) && !normalized.contains("unplug")) {
			result = appendRecommendation(result, buildBatteryUnplugRecommendation(summaryContext.stats()));
			normalized = result.toLowerCase(Locale.ROOT);
		}

		if (isBatteryNearFull(summaryContext.stats()) && !normalized.contains("charger")) {
			result = appendRecommendation(result, buildBatteryFullConditionalRecommendation(summaryContext.stats()));
			normalized = result.toLowerCase(Locale.ROOT);
		}

		if (hasSsdLowHealth(summaryContext.stats())
				&& !normalized.contains("backup")
				&& !normalized.contains("back up")) {
			result = appendRecommendation(result, buildSsdBackupRecommendation(summaryContext.stats()));
			normalized = result.toLowerCase(Locale.ROOT);
		}

		Optional<StorageStats> storage = storageStats();
		if (storage.filter(this::isStorageLow).isPresent()
				&& !normalized.contains("free space")
				&& !normalized.contains("clean")
				&& !normalized.contains("move files")) {
			result = appendRecommendation(result, buildStorageCleanupRecommendation(storage.get()));
		}

		return result;
	}

	private String appendRecommendation(String body, String recommendation) {
		if (body.contains("Recommendations:")) {
			return body + "\n- " + recommendation;
		}

		return body + "\n\nRecommendations:\n- " + recommendation;
	}

	private boolean hasSsdLowHealth(List<MetricStats> stats) {
		return metric(stats, MetricType.SSD_HEALTH_PERCENT)
				.filter(s -> s.latest() <= monitoringProperties.ssdLowHealthPercentThreshold())
				.isPresent();
	}

	private String buildBatteryUnplugRecommendation(List<MetricStats> stats) {
		double level = metric(stats, MetricType.BATTERY_PERCENT)
				.map(MetricStats::latest)
				.orElse(100.0);

		return "Battery is at " + format(level) + "% and still charging. Unplug the charger to protect battery health.";
	}

	private String buildBatteryFullConditionalRecommendation(List<MetricStats> stats) {
		double level = metric(stats, MetricType.BATTERY_PERCENT)
				.map(MetricStats::latest)
				.orElse(100.0);

		return "Battery is full (" + format(level) + "%). If the charger is still plugged in, unplug it to reduce long-term battery wear.";
	}

	private String buildSsdBackupRecommendation(List<MetricStats> stats) {
		double health = metric(stats, MetricType.SSD_HEALTH_PERCENT)
				.map(MetricStats::latest)
				.orElse(0.0);

		return "SSD health is low (" + format(health) + "%). Back up important files now and plan SSD replacement.";
	}

	private boolean isStorageLow(StorageStats storageStats) {
		return storageStats.freeGb() <= monitoringProperties.storageLowFreeGbThreshold();
	}

	private String buildStorageCleanupRecommendation(StorageStats storageStats) {
		return "Only " + format(storageStats.freeGb()) + " GB free storage remains. Clean unnecessary files or move data to external/cloud storage.";
	}

	private String buildStorageSummary() {
		Optional<StorageStats> storage = storageStats();
		if (storage.isEmpty()) {
			return "unknown";
		}

		StorageStats s = storage.get();
		return format(s.totalGb()) + " GB total, "
				+ format(s.usedGb()) + " GB used, "
				+ format(s.freeGb()) + " GB free";
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

	private Optional<StorageStats> storageStats() {
		long totalBytes = 0L;
		long freeBytes = 0L;
		File[] roots = File.listRoots();
		if (roots == null || roots.length == 0) {
			return Optional.empty();
		}

		for (File root : roots) {
			totalBytes += Math.max(0L, root.getTotalSpace());
			freeBytes += Math.max(0L, root.getUsableSpace());
		}

		if (totalBytes <= 0L) {
			return Optional.empty();
		}

		double totalGb = totalBytes / 1_000_000_000.0;
		double freeGb = freeBytes / 1_000_000_000.0;
		double usedGb = Math.max(0.0, totalGb - freeGb);

		return Optional.of(new StorageStats(totalGb, usedGb, freeGb));
	}

	private record StorageStats(double totalGb, double usedGb, double freeGb) {
	}

	private record SummaryContext(String rawSummary, String priority, List<String> highlights, List<MetricStats> stats) {
	}

	private record MetricStats(MetricType type, double latest, double avg, double min, double max, double delta,
							   int samples, String trend) {

		private static MetricStats from(MetricType type, List<MetricSnapshot> history) {
			MetricSnapshot first = history.getFirst();
			MetricSnapshot last = history.getLast();

			double min = history.stream().mapToDouble(MetricSnapshot::value).min().orElse(last.value());
			double max = history.stream().mapToDouble(MetricSnapshot::value).max().orElse(last.value());
			double avg = history.stream().mapToDouble(MetricSnapshot::value).average().orElse(last.value());
			double delta = isCumulativeMetric(type)
					? computeCumulativeDelta(history)
					: last.value() - first.value();

			String trend = Math.abs(delta) < 0.01
					? "stable"
					: (isCumulativeMetric(type) ? "up" : (delta > 0 ? "up" : "down"));

			return new MetricStats(type, last.value(), avg, min, max, delta, history.size(), trend);
		}
	}
}

