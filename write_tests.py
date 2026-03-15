import os

BASE = r"C:\Users\moham\Desktop\sentria-agent\src\test\java\com\sentria"

FILES = {}

# ─────────────────────────────────────────────────────────────────────────────
FILES[os.path.join(BASE, "finding", "BatteryFindingServiceTest.java")] = """\
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
"""

# ─────────────────────────────────────────────────────────────────────────────
FILES[os.path.join(BASE, "finding", "SsdFindingServiceTest.java")] = """\
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
"""

# ─────────────────────────────────────────────────────────────────────────────
FILES[os.path.join(BASE, "context", "BehaviorCorrelationServiceTest.java")] = """\
package com.sentria.context;

import com.sentria.application.port.BehaviorSessionStore;
import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BehaviorCorrelationService}.
 */
@ExtendWith(MockitoExtension.class)
class BehaviorCorrelationServiceTest {

    @Mock private BehaviorSessionStore store;
    private BehaviorCorrelationService service;

    private final Instant since = Instant.now().minusSeconds(1_209_600);

    @BeforeEach
    void setUp() {
        service = new BehaviorCorrelationService(store);
    }

    @Test
    void returnsNull_whenNoHeavyWriteSessions() {
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of());

        assertThat(service.findLikelySsdWearContributor(since)).isNull();
    }

    @Test
    void returnsNoDominantPattern_whenNoOverlapWithVideoOrGaming() {
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.HEAVY_WRITE_ACTIVITY,
                        Instant.now().minusSeconds(100), Instant.now().minusSeconds(50))));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.VIDEO_EDITING), any()))
                .thenReturn(List.of());
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.GAMING), any()))
                .thenReturn(List.of());

        assertThat(service.findLikelySsdWearContributor(since))
                .contains("no dominant workload pattern");
    }

    @Test
    void returnsVideoEditing_whenVideoOverlapsMore() {
        Instant s = Instant.now().minusSeconds(3600);
        Instant e = Instant.now().minusSeconds(1800);

        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.HEAVY_WRITE_ACTIVITY, s, e)));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.VIDEO_EDITING), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.VIDEO_EDITING,
                        s.minusSeconds(60), e.plusSeconds(60))));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.GAMING), any()))
                .thenReturn(List.of());

        assertThat(service.findLikelySsdWearContributor(since))
                .contains("video editing");
    }

    @Test
    void returnsGaming_whenGamingOverlapsMore() {
        Instant s = Instant.now().minusSeconds(3600);
        Instant e = Instant.now().minusSeconds(1800);

        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.HEAVY_WRITE_ACTIVITY, s, e)));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.VIDEO_EDITING), any()))
                .thenReturn(List.of());
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.GAMING), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.GAMING,
                        s.minusSeconds(60), e.plusSeconds(60))));

        assertThat(service.findLikelySsdWearContributor(since)).contains("gaming");
    }

    @Test
    void nonOverlappingSessions_doNotCount() {
        Instant hwEnd   = Instant.now().minusSeconds(7200);
        Instant hwStart = hwEnd.minusSeconds(3600);
        Instant vidStart = Instant.now().minusSeconds(1800);
        Instant vidEnd   = Instant.now().minusSeconds(900);

        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.HEAVY_WRITE_ACTIVITY), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.HEAVY_WRITE_ACTIVITY, hwStart, hwEnd)));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.VIDEO_EDITING), any()))
                .thenReturn(List.of(sess(BehaviorSessionType.VIDEO_EDITING, vidStart, vidEnd)));
        when(store.findSessionsByTypeSince(eq(BehaviorSessionType.GAMING), any()))
                .thenReturn(List.of());

        assertThat(service.findLikelySsdWearContributor(since))
                .contains("no dominant workload pattern");
    }

    private BehaviorSession sess(BehaviorSessionType type, Instant start, Instant end) {
        return new BehaviorSession("id", "local-device", type, start, end, null);
    }
}
"""

# ─────────────────────────────────────────────────────────────────────────────
FILES[os.path.join(BASE, "behavior", "BehaviorDetectionServiceTest.java")] = """\
package com.sentria.behavior;

import com.sentria.application.behavior.RunningProcessProvider;
import com.sentria.application.port.BehaviorSessionStore;
import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BehaviorDetectionService}.
 */
@ExtendWith(MockitoExtension.class)
class BehaviorDetectionServiceTest {

    @Mock private BehaviorSessionStore sessionStore;
    @Mock private RunningProcessProvider processProvider;

    private BehaviorDetectionService service;

    @BeforeEach
    void setUp() {
        service = new BehaviorDetectionService(sessionStore, processProvider);
    }

    @Test
    void detectBehaviors_opensVideoEditingSession_whenPremiereRunning() {
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("premiere.exe"));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.VIDEO_EDITING)).thenReturn(null);
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.GAMING)).thenReturn(null);

        service.detectBehaviors();

        ArgumentCaptor<BehaviorSession> cap = ArgumentCaptor.forClass(BehaviorSession.class);
        verify(sessionStore, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues())
                .anyMatch(s -> s.sessionType() == BehaviorSessionType.VIDEO_EDITING);
    }

    @Test
    void detectBehaviors_opensGamingSession_whenSteamRunning() {
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("steam.exe"));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.VIDEO_EDITING)).thenReturn(null);
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.GAMING)).thenReturn(null);

        service.detectBehaviors();

        ArgumentCaptor<BehaviorSession> cap = ArgumentCaptor.forClass(BehaviorSession.class);
        verify(sessionStore, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues())
                .anyMatch(s -> s.sessionType() == BehaviorSessionType.GAMING);
    }

    @Test
    void detectBehaviors_doesNotOpenSession_whenAlreadyOpen() {
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("premiere.exe"));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.VIDEO_EDITING))
                .thenReturn(openSession(BehaviorSessionType.VIDEO_EDITING));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.GAMING)).thenReturn(null);

        service.detectBehaviors();

        verify(sessionStore, never()).save(any());
    }

    @Test
    void detectBehaviors_closesSession_whenProcessNoLongerRunning() {
        BehaviorSession open = openSession(BehaviorSessionType.VIDEO_EDITING);
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("chrome.exe"));
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.VIDEO_EDITING)).thenReturn(open);
        when(sessionStore.findOpenSessionByType(BehaviorSessionType.GAMING)).thenReturn(null);

        service.detectBehaviors();

        verify(sessionStore).closeSession(eq(open.id()), any(Instant.class));
    }

    @Test
    void detectBehaviors_doesNothingForUnknownProcesses() {
        when(processProvider.getNormalizedProcessNames()).thenReturn(List.of("notepad.exe"));
        when(sessionStore.findOpenSessionByType(any())).thenReturn(null);

        service.detectBehaviors();

        verify(sessionStore, never()).save(any());
    }

    private BehaviorSession openSession(BehaviorSessionType type) {
        return new BehaviorSession("test-id", "local-device", type,
                Instant.now().minusSeconds(60), null, "process.exe");
    }
}
"""

# ─────────────────────────────────────────────────────────────────────────────
FILES[os.path.join(BASE, "application", "monitoring", "MetricCollectionOrchestratorTest.java")] = """\
package com.sentria.application.monitoring;

import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.config.MonitoringProperties;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MetricCollectionOrchestrator}.
 */
@ExtendWith(MockitoExtension.class)
class MetricCollectionOrchestratorTest {

    @Mock private MetricSnapshotStore store;
    @Mock private MonitoringProperties properties;

    @Test
    void collectOnce_savesAllSnapshotsFromCollector() {
        FakeCollector plugin = new FakeCollector("hardware",
                List.of(new MetricSnapshot("dev", MetricType.CPU_USAGE_PERCENT, 50.0, Instant.now())));
        when(properties.collectors()).thenReturn(List.of());

        new MetricCollectionOrchestrator(List.of(plugin), store, properties).collectOnce();

        verify(store, times(1)).save(any());
    }

    @Test
    void collectOnce_skipsCollector_notInAllowList() {
        FakeCollector plugin = new FakeCollector("hardware",
                List.of(new MetricSnapshot("dev", MetricType.CPU_USAGE_PERCENT, 50.0, Instant.now())));
        when(properties.collectors()).thenReturn(List.of("ssd"));

        new MetricCollectionOrchestrator(List.of(plugin), store, properties).collectOnce();

        verify(store, never()).save(any());
    }

    @Test
    void collectOnce_runsCollector_whenAllowListIsEmpty() {
        FakeCollector plugin = new FakeCollector("ssd",
                List.of(new MetricSnapshot("dev", MetricType.SSD_HEALTH_PERCENT, 90.0, Instant.now())));
        when(properties.collectors()).thenReturn(List.of());

        new MetricCollectionOrchestrator(List.of(plugin), store, properties).collectOnce();

        verify(store, times(1)).save(any());
    }

    @Test
    void collectOnce_continuesWithOtherCollectors_whenOneThrows() {
        FakeCollector failing = new FakeCollector("bad", null) {
            @Override public List<MetricSnapshot> collect(Instant t) {
                throw new RuntimeException("boom");
            }
        };
        FakeCollector good = new FakeCollector("good",
                List.of(new MetricSnapshot("dev", MetricType.RAM_USAGE_PERCENT, 40.0, Instant.now())));
        when(properties.collectors()).thenReturn(List.of());

        new MetricCollectionOrchestrator(List.of(failing, good), store, properties).collectOnce();

        verify(store, times(1)).save(any());
    }

    @Test
    void collectOnce_noSnapshotsSaved_whenCollectorReturnsEmptyList() {
        FakeCollector plugin = new FakeCollector("empty", List.of());
        when(properties.collectors()).thenReturn(List.of());

        new MetricCollectionOrchestrator(List.of(plugin), store, properties).collectOnce();

        verify(store, never()).save(any());
    }

    /** Minimal in-process collector stub. */
    private static class FakeCollector implements MetricCollectorPlugin {
        private final String name;
        private final List<MetricSnapshot> snaps;

        FakeCollector(String name, List<MetricSnapshot> snaps) {
            this.name = name;
            this.snaps = snaps;
        }

        @Override public String collectorName() { return name; }
        @Override public List<MetricSnapshot> collect(Instant t) { return snaps; }
    }
}
"""

for path, content in FILES.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Written:", path)

print("All done.")

