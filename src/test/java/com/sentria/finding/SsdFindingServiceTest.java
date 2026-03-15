package com.sentria.finding;

import com.sentria.application.monitoring.SsdMonitoringStatus;
import com.sentria.application.port.FindingStore;
import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.application.port.NotificationSender;
import com.sentria.context.BehaviorCorrelationService;
import com.sentria.domain.Confidence;
import com.sentria.domain.Finding;
import com.sentria.domain.FindingType;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import com.sentria.domain.Severity;
import com.sentria.notification.FormattedNotification;
import com.sentria.notification.NotificationFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SsdFindingService}.
 */
@ExtendWith(MockitoExtension.class)
class SsdFindingServiceTest {

    @Mock private MetricSnapshotStore metricRepository;
    @Mock private FindingStore findingRepository;
    @Mock private FindingFactory findingFactory;
    @Mock private BehaviorCorrelationService behaviorCorrelationService;
    @Mock private NotificationFormatter formatter;
    @Mock private NotificationSender notificationSender;

    private SsdMonitoringStatus status;
    private SsdFindingService service;

    @BeforeEach
    void setUp() {
        status = new SsdMonitoringStatus();
        service = new SsdFindingService(metricRepository, findingRepository, findingFactory,
                behaviorCorrelationService, formatter, notificationSender, status);
    }

    @Test
    void detectWearAcceleration_skips_whenSsdUnavailable() {
        // status.isAvailable() == false by default
        service.detectWearAcceleration();
        verify(metricRepository, never()).findByTypeSince(any(), any());
    }

    @Test
    void detectWearAcceleration_skips_whenFewerThanTwoDataPoints() {
        status.markAvailable("ok");
        when(metricRepository.findByTypeSince(eq(MetricType.SSD_HEALTH_PERCENT), any()))
                .thenReturn(List.of(snap(90.0)));

        service.detectWearAcceleration();

        verify(findingRepository, never()).save(any());
    }

    @Test
    void detectWearAcceleration_skips_whenDropLessThan3Percent() {
        status.markAvailable("ok");
        when(metricRepository.findByTypeSince(eq(MetricType.SSD_HEALTH_PERCENT), any()))
                .thenReturn(List.of(snap(90.0), snap(88.0)));

        service.detectWearAcceleration();

        verify(findingRepository, never()).save(any());
    }

    @Test
    void detectWearAcceleration_skips_whenAlreadySentRecently() {
        status.markAvailable("ok");
        when(metricRepository.findByTypeSince(eq(MetricType.SSD_HEALTH_PERCENT), any()))
                .thenReturn(List.of(snap(90.0), snap(85.0)));
        when(findingRepository.existsByTypeSince(eq(FindingType.SSD_WEAR_ACCELERATING), anyString()))
                .thenReturn(true);

        service.detectWearAcceleration();

        verify(findingRepository, never()).save(any());
    }

    @Test
    void detectWearAcceleration_savesFindingAndSendsNotification_whenDropExceeds3Percent() {
        status.markAvailable("ok");
        when(metricRepository.findByTypeSince(eq(MetricType.SSD_HEALTH_PERCENT), any()))
                .thenReturn(List.of(snap(90.0), snap(85.0)));
        when(findingRepository.existsByTypeSince(anyString(), anyString())).thenReturn(false);
        when(behaviorCorrelationService.findLikelySsdWearContributor(any())).thenReturn("gaming");

        Finding fake = new Finding("id", FindingType.SSD_WEAR_ACCELERATING, Severity.HIGH,
                Confidence.MEDIUM, List.of("fact"), "gaming", List.of("rec"), Instant.now());
        when(findingFactory.ssdWearAccelerating(90.0, 85.0, 14, "gaming")).thenReturn(fake);
        when(formatter.format(fake)).thenReturn(new FormattedNotification("t", "b", "high"));

        service.detectWearAcceleration();

        verify(findingRepository).save(fake);
        verify(notificationSender).send(any());
    }

    @Test
    void detectWearAcceleration_exactlyThreePercentDrop_triggersAlert() {
        status.markAvailable("ok");
        when(metricRepository.findByTypeSince(eq(MetricType.SSD_HEALTH_PERCENT), any()))
                .thenReturn(List.of(snap(90.0), snap(87.0)));
        when(findingRepository.existsByTypeSince(anyString(), anyString())).thenReturn(false);
        when(behaviorCorrelationService.findLikelySsdWearContributor(any())).thenReturn(null);
        when(findingFactory.ssdWearAccelerating(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(new Finding("id", FindingType.SSD_WEAR_ACCELERATING, Severity.HIGH,
                        Confidence.MEDIUM, List.of("f"), null, List.of("r"), Instant.now()));
        when(formatter.format(any())).thenReturn(new FormattedNotification("t", "b", "high"));

        service.detectWearAcceleration();

        verify(findingRepository).save(any());
    }

    private MetricSnapshot snap(double value) {
        return new MetricSnapshot("local-device", MetricType.SSD_HEALTH_PERCENT, value, Instant.now());
    }
}
