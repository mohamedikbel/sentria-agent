package com.sentria.finding;

import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.application.port.FindingStore;
import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.application.port.NotificationSender;
import com.sentria.notification.FormattedNotification;
import com.sentria.notification.NotificationFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Detects when the battery is fully charged while the charger is still connected.
 *
 * <p>The check runs every {@code monitoring.interval-seconds}. A notification is only
 * sent once per 6-hour window to avoid spamming the user.
 */
@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class BatteryFindingService {

    private final MetricSnapshotStore metricSnapshotRepository;
    private final FindingStore findingRepository;
    @Qualifier("compositeNotificationFormatter")
    private final NotificationFormatter formatter;
    private final NotificationSender notificationSender;
    private final FindingFactory findingFactory;

    /**
     * Scheduled detection logic.
     * <ol>
     *   <li>Reads the latest {@code BATTERY_PERCENT} and {@code BATTERY_CHARGING} snapshots.</li>
     *   <li>Triggers a finding only when the battery is ≥ 99.5 % AND the charger is connected.</li>
     *   <li>Skips if the same finding was already emitted in the last 6 hours.</li>
     * </ol>
     */
    @Scheduled(fixedRateString = "${monitoring.interval-seconds}000")
    public void detectBatteryFull() {
        MetricSnapshot batteryPercent = metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT);
        MetricSnapshot charging = metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING);

        // Skip if either metric has not been collected yet.
        if (batteryPercent == null || charging == null) {
            return;
        }

        boolean isFull = batteryPercent.value() >= 99.5;
        boolean isCharging = charging.value() >= 0.5;

        if (!isFull || !isCharging) {
            return;
        }

        // Deduplicate: do not send if the same finding was already emitted in the last 6 hours.
        String since = Instant.now().minus(6, ChronoUnit.HOURS).toString();
        boolean alreadySentRecently = findingRepository.existsByTypeSince(
                FindingType.BATTERY_FULLY_CHARGED,
                since
        );

        if (alreadySentRecently) {
            log.info("Battery full finding already sent recently, skipping");
            return;
        }

        Finding finding = findingFactory.batteryFullyCharged();

        findingRepository.save(finding);

        FormattedNotification notification = formatter.format(finding);
        notificationSender.send(notification);

        log.info("Battery full finding created and notification sent");
    }
}