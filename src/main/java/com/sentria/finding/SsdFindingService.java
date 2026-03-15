package com.sentria.finding;

import com.sentria.application.monitoring.SsdMonitoringStatus;
import com.sentria.application.port.FindingStore;
import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.application.port.NotificationSender;
import com.sentria.context.BehaviorCorrelationService;
import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.notification.FormattedNotification;
import com.sentria.notification.NotificationFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Detects accelerated SSD wear by comparing the health percentage over a 14-day window.
 *
 * <p>A finding is raised when the drop exceeds 3 percentage points. The likely workload
 * contributor is identified by {@link BehaviorCorrelationService} and included in the finding.
 * A deduplication guard prevents re-sending the same alert within 24 hours.
 */
@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class SsdFindingService {

    private final MetricSnapshotStore metricRepository;
    private final FindingStore findingRepository;
    private final FindingFactory findingFactory;
    private final BehaviorCorrelationService behaviorCorrelationService;
    @Qualifier("compositeNotificationFormatter")
    private final NotificationFormatter formatter;
    private final NotificationSender notificationSender;
    /** Tracks whether SSD monitoring is currently active (smartctl or OSHI available). */
    private final SsdMonitoringStatus status;

    /**
     * Scheduled wear-acceleration detection.
     * <ol>
     *   <li>Skips if SSD monitoring is not available on this device.</li>
     *   <li>Fetches the 14-day health history and computes the drop.</li>
     *   <li>Raises a finding only when the drop is ≥ 3 % and no duplicate was sent in the last 24 h.</li>
     * </ol>
     */
    @Scheduled(fixedRateString = "#{${monitoring.interval-seconds} * 1000}")
    public void detectWearAcceleration() {
        // Guard: skip entirely if smartctl / OSHI could not read SSD data.
        if (!status.isAvailable()) {
            return;
        }

        Instant since = Instant.now().minus(14, ChronoUnit.DAYS);

        List<MetricSnapshot> history =
                metricRepository.findByTypeSince(MetricType.SSD_HEALTH_PERCENT, since);

        // Need at least two data points to calculate a delta.
        if (history.size() < 2) {
            return;
        }

        MetricSnapshot first = history.get(0);
        MetricSnapshot last = history.get(history.size() - 1);

        double startHealth = first.value();
        double endHealth = last.value();
        double drop = startHealth - endHealth;

        // Only flag when the wear is significant (> 3 percentage points in 14 days).
        if (drop < 3) {
            return;
        }

        // Deduplicate: suppress if an identical finding was already sent in the last 24 hours.
        boolean alreadySent = findingRepository.existsByTypeSince(
                FindingType.SSD_WEAR_ACCELERATING,
                Instant.now().minus(24, ChronoUnit.HOURS).toString()
        );

        if (alreadySent) {
            return;
        }

        // Correlate with behavior sessions to explain *why* the wear spiked.
        String likelyContributor = behaviorCorrelationService.findLikelySsdWearContributor(since);

        Finding finding = findingFactory.ssdWearAccelerating(
                startHealth,
                endHealth,
                14,
                likelyContributor
        );

        findingRepository.save(finding);

        FormattedNotification notification = formatter.format(finding);
        notificationSender.send(notification);

        log.info("SSD wear acceleration detected with contributor={}", likelyContributor);
    }
}