package com.sentria.application.monitoring;

import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.config.MonitoringProperties;
import com.sentria.domain.MetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Central coordinator for all metric collection.
 *
 * <p>On every tick it asks each registered {@link MetricCollectorPlugin} to produce
 * fresh snapshots, then persists them via {@link MetricSnapshotStore}.
 * Collectors are discovered automatically by Spring (all {@code @Service} beans that
 * implement the interface). An optional allow-list in {@code monitoring.collectors}
 * can restrict which collectors are active.
 */
@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class MetricCollectionOrchestrator {

	private final List<MetricCollectorPlugin> collectors;
	private final MetricSnapshotStore metricSnapshotStore;
	private final MonitoringProperties monitoringProperties;

	/** Scheduled entry-point – delegates to {@link #collectOnce()}. */
	@Scheduled(fixedRateString = "#{${monitoring.interval-seconds} * 1000}")
	public void collectAll() {
		collectOnce();
	}

	/**
	 * Runs a single collection pass across all enabled collectors.
	 * Errors in individual collectors are caught and logged so that one failing
	 * collector never blocks the others.
	 */
	public void collectOnce() {
		Instant collectedAt = Instant.now();
		Set<String> enabledCollectors = resolveEnabledCollectors();
		Set<String> availableCollectors = collectors.stream()
				.map(c -> c.collectorName().toLowerCase(Locale.ROOT))
				.collect(java.util.stream.Collectors.toSet());

		// Warn about collector names in config that don't match any registered bean.
		if (!enabledCollectors.isEmpty()) {
			Set<String> unknown = enabledCollectors.stream()
					.filter(name -> !availableCollectors.contains(name))
					.collect(java.util.stream.Collectors.toSet());
			if (!unknown.isEmpty()) {
				log.warn("Unknown collectors in configuration: {}. Available collectors: {}", unknown, availableCollectors);
			}
		}

		for (MetricCollectorPlugin collector : collectors) {
			// Skip collectors not in the allow-list (empty list = all enabled).
			if (!enabledCollectors.isEmpty()
					&& !enabledCollectors.contains(collector.collectorName().toLowerCase(Locale.ROOT))) {
				continue;
			}

			try {
				List<MetricSnapshot> snapshots = collector.collect(collectedAt);
				snapshots.forEach(metricSnapshotStore::save);

				if (!snapshots.isEmpty()) {
					log.debug("Collector {} produced {} snapshot(s)", collector.collectorName(), snapshots.size());
				}
			} catch (Exception e) {
				log.error("Collector {} failed", collector.collectorName(), e);
			}
		}
	}

	/**
	 * Parses the allow-list from configuration.
	 *
	 * @return lower-cased collector names, or an empty set meaning "all collectors enabled".
	 */
	private Set<String> resolveEnabledCollectors() {
		if (monitoringProperties.collectors() == null || monitoringProperties.collectors().isEmpty()) {
			return Set.of();
		}

		Set<String> result = new HashSet<>();
		for (String value : monitoringProperties.collectors()) {
			if (value == null || value.isBlank()) {
				continue;
			}
			result.add(value.trim().toLowerCase(Locale.ROOT));
		}

		return result;
	}
}
