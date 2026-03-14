package com.sentria.finding;

import com.sentria.domain.Confidence;
import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.domain.Severity;
import com.sentria.notification.FormattedNotification;
import com.sentria.notification.NotificationFormatter;
import com.sentria.notification.NtfySender;
import com.sentria.repository.FindingRepository;
import com.sentria.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@DependsOn("flyway")

@RequiredArgsConstructor

public class BatteryFindingService {

    private final MetricSnapshotRepository metricSnapshotRepository;
    private final FindingRepository findingRepository;
    private  NotificationFormatter formatter;
    private final NtfySender ntfySender;
    private final FindingFactory findingFactory;

    @Scheduled(
            fixedRateString = "${monitoring.interval-seconds}000")
    public void detectBatteryFull() {
        MetricSnapshot batteryPercent = metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT);
        MetricSnapshot charging = metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING);

        if (batteryPercent == null || charging == null) {
            return;
        }

        boolean isFull = batteryPercent.value() >= 99.5;
        boolean isCharging = charging.value() >= 0.5;

        if (!isFull || !isCharging) {
            return;
        }

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
        ntfySender.send(notification);

        log.info("Battery full finding created and notification sent");
    }
}