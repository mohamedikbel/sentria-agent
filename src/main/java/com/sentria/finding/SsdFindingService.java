package com.sentria.finding;

import com.sentria.collector.SsdMonitoringStatus;
import com.sentria.context.BehaviorCorrelationService;
import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
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

@Slf4j
@Service
@DependsOn("flyway")

@RequiredArgsConstructor
public class SsdFindingService {

    private final MetricSnapshotRepository metricRepository;
    private final FindingRepository findingRepository;
    private final FindingFactory findingFactory;
    private final BehaviorCorrelationService behaviorCorrelationService;
    private  NotificationFormatter formatter;
    private final NtfySender ntfySender;
    private final SsdMonitoringStatus status;

    @Scheduled(fixedRateString = "#{${monitoring.interval-seconds} * 1000}")
    public void detectWearAcceleration() {
        if (!status.isAvailable()) {
            return;
        }

        Instant since = Instant.now().minus(14, ChronoUnit.DAYS);

        List<MetricSnapshot> history =
                metricRepository.findByTypeSince(MetricType.SSD_HEALTH_PERCENT, since);

        if (history.size() < 2) {
            return;
        }

        MetricSnapshot first = history.get(0);
        MetricSnapshot last = history.get(history.size() - 1);

        double startHealth = first.value();
        double endHealth = last.value();
        double drop = startHealth - endHealth;

        if (drop < 3) {
            return;
        }

        boolean alreadySent = findingRepository.existsByTypeSince(
                FindingType.SSD_WEAR_ACCELERATING,
                Instant.now().minus(24, ChronoUnit.HOURS).toString()
        );

        if (alreadySent) {
            return;
        }

        String likelyContributor = behaviorCorrelationService.findLikelySsdWearContributor(since);

        Finding finding = findingFactory.ssdWearAccelerating(
                startHealth,
                endHealth,
                14,
                likelyContributor
        );

        findingRepository.save(finding);

        FormattedNotification notification = formatter.format(finding);
        ntfySender.send(notification);

        log.info("SSD wear acceleration detected with contributor={}", likelyContributor);
    }
}