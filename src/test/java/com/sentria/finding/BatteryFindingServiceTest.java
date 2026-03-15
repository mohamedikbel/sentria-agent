package com.sentria.finding;

import com.sentria.application.port.FindingStore;
import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.application.port.NotificationSender;
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
 * Unit tests for {@link BatteryFindingService}.
 * All dependencies are mocked; no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
class BatteryFindingServiceTest {

    @Mock private MetricSnapshotStore metricSnapshotRepository;
    @Mock private FindingStore findingRepository;
    @Mock private NotificationFormatter formatter;
    @Mock private NotificationSender notificationSender;
    @Mock private FindingFactory findingFactory;

    private BatteryFindingService service;

    @BeforeEach
    void setUp() {
        service = new BatteryFindingService(
                metricSnapshotRepository, findingRepository, formatter,
                notificationSender, findingFactory);
    }

    @Test
    void detectBatteryFull_doesNothing_whenBatteryPercentMissing() {
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT)).thenReturn(null);
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING))
                .thenReturn(snap(MetricType.BATTERY_CHARGING, 1.0));

        service.detectBatteryFull();

        verify(findingRepository, never()).save(any());
    }

    @Test
    void detectBatteryFull_doesNothing_whenChargingMissing() {
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT))
                .thenReturn(snap(MetricType.BATTERY_PERCENT, 100.0));
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING)).thenReturn(null);

        service.detectBatteryFull();

        verify(findingRepository, never()).save(any());
    }

    @Test
    void detectBatteryFull_doesNothing_whenBatteryBelowThreshold() {
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT))
                .thenReturn(snap(MetricType.BATTERY_PERCENT, 80.0));
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING))
                .thenReturn(snap(MetricType.BATTERY_CHARGING, 1.0));

        service.detectBatteryFull();

        verify(findingRepository, never()).save(any());
    }

    @Test
    void detectBatteryFull_doesNothing_whenNotCharging() {
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT))
                .thenReturn(snap(MetricType.BATTERY_PERCENT, 100.0));
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING))
                .thenReturn(snap(MetricType.BATTERY_CHARGING, 0.0));

        service.detectBatteryFull();

        verify(findingRepository, never()).save(any());
    }

    @Test
    void detectBatteryFull_doesNothing_whenAlreadySentRecently() {
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT))
                .thenReturn(snap(MetricType.BATTERY_PERCENT, 100.0));
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING))
                .thenReturn(snap(MetricType.BATTERY_CHARGING, 1.0));
        when(findingRepository.existsByTypeSince(eq(FindingType.BATTERY_FULLY_CHARGED), anyString()))
                .thenReturn(true);

        service.detectBatteryFull();

        verify(findingRepository, never()).save(any());
        verify(notificationSender, never()).send(any());
    }

    @Test
    void detectBatteryFull_savesFindingAndSendsNotification_whenConditionsMet() {
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT))
                .thenReturn(snap(MetricType.BATTERY_PERCENT, 100.0));
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING))
                .thenReturn(snap(MetricType.BATTERY_CHARGING, 1.0));
        when(findingRepository.existsByTypeSince(anyString(), anyString())).thenReturn(false);

        Finding fakeFinding = fakeFinding(FindingType.BATTERY_FULLY_CHARGED, Severity.LOW);
        when(findingFactory.batteryFullyCharged()).thenReturn(fakeFinding);
        FormattedNotification fakeNotif = new FormattedNotification("Battery fully charged", "body", "low");
        when(formatter.format(fakeFinding)).thenReturn(fakeNotif);

        service.detectBatteryFull();

        verify(findingRepository).save(fakeFinding);
        verify(notificationSender).send(fakeNotif);
    }

    @Test
    void detectBatteryFull_triggersWith995PercentCharge() {
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_PERCENT))
                .thenReturn(snap(MetricType.BATTERY_PERCENT, 99.5));
        when(metricSnapshotRepository.findLatestByType(MetricType.BATTERY_CHARGING))
                .thenReturn(snap(MetricType.BATTERY_CHARGING, 1.0));
        when(findingRepository.existsByTypeSince(anyString(), anyString())).thenReturn(false);
        when(findingFactory.batteryFullyCharged())
                .thenReturn(fakeFinding(FindingType.BATTERY_FULLY_CHARGED, Severity.LOW));
        when(formatter.format(any())).thenReturn(new FormattedNotification("t", "b", "low"));

        service.detectBatteryFull();

        verify(findingRepository).save(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MetricSnapshot snap(MetricType type, double value) {
        return new MetricSnapshot("local-device", type, value, Instant.now());
    }

    private Finding fakeFinding(String type, Severity severity) {
        return new Finding("id", type, severity, Confidence.HIGH,
                List.of("fact"), null, List.of("rec"), Instant.now());
    }
}
