package com.sentria.behavior;

import com.sentria.application.port.BehaviorSessionStore;
import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DiskActivityService}.
 * Verifies heavy-write session lifecycle without a real database or OSHI.
 */
@ExtendWith(MockitoExtension.class)
class DiskActivityServiceTest {

    @Mock private MetricSnapshotStore metricRepository;
    @Mock private BehaviorSessionStore behaviorRepository;

    private DiskActivityService service;

    @BeforeEach
    void setUp() {
        service = new DiskActivityService(metricRepository, behaviorRepository);
    }

    // ── fewer than 2 snapshots ────────────────────────────────────────────────

    @Test
    void detectHeavyWriteActivity_doesNothing_whenNoSnapshots() {
        when(metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2))
                .thenReturn(List.of());

        service.detectHeavyWriteActivity();

        verify(behaviorRepository, never()).save(any());
        verify(behaviorRepository, never()).closeSession(any(), any());
    }

    @Test
    void detectHeavyWriteActivity_doesNothing_whenOnlyOneSnapshot() {
        when(metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2))
                .thenReturn(List.of(snap(100.0)));

        service.detectHeavyWriteActivity();

        verify(behaviorRepository, never()).save(any());
        verify(behaviorRepository, never()).closeSession(any(), any());
    }

    // ── delta below threshold ────────────────────────────────────────────────

    @Test
    void detectHeavyWriteActivity_closesOpenSession_whenDeltaBelowThreshold() {
        when(metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2))
                .thenReturn(List.of(snap(104.9), snap(100.0)));   // delta = 4.9 GB < 5.0
        BehaviorSession openSession = openSession("session-1");
        when(behaviorRepository.findOpenSessionByType(BehaviorSessionType.HEAVY_WRITE_ACTIVITY))
                .thenReturn(openSession);

        service.detectHeavyWriteActivity();

        verify(behaviorRepository).closeSession(eq("session-1"), any(Instant.class));
        verify(behaviorRepository, never()).save(any());
    }

    @Test
    void detectHeavyWriteActivity_doesNothing_whenDeltaBelowThresholdAndNoOpenSession() {
        when(metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2))
                .thenReturn(List.of(snap(103.0), snap(100.0)));   // delta = 3.0 GB
        when(behaviorRepository.findOpenSessionByType(BehaviorSessionType.HEAVY_WRITE_ACTIVITY))
                .thenReturn(null);

        service.detectHeavyWriteActivity();

        verify(behaviorRepository, never()).save(any());
        verify(behaviorRepository, never()).closeSession(any(), any());
    }

    // ── delta at/above threshold ─────────────────────────────────────────────

    @Test
    void detectHeavyWriteActivity_opensNewSession_whenDeltaExceedsThreshold() {
        when(metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2))
                .thenReturn(List.of(snap(106.0), snap(100.0)));   // delta = 6.0 GB >= 5.0
        when(behaviorRepository.findOpenSessionByType(BehaviorSessionType.HEAVY_WRITE_ACTIVITY))
                .thenReturn(null);

        service.detectHeavyWriteActivity();

        ArgumentCaptor<BehaviorSession> cap = ArgumentCaptor.forClass(BehaviorSession.class);
        verify(behaviorRepository).save(cap.capture());

        BehaviorSession saved = cap.getValue();
        assertThat(saved.sessionType()).isEqualTo(BehaviorSessionType.HEAVY_WRITE_ACTIVITY);
        assertThat(saved.id()).isNotBlank();
        assertThat(saved.startedAt()).isNotNull();
        assertThat(saved.endedAt()).isNull();   // session still open
    }

    @Test
    void detectHeavyWriteActivity_opensSession_atExactly5GbThreshold() {
        when(metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2))
                .thenReturn(List.of(snap(105.0), snap(100.0)));   // delta = 5.0 GB (exactly threshold)
        when(behaviorRepository.findOpenSessionByType(BehaviorSessionType.HEAVY_WRITE_ACTIVITY))
                .thenReturn(null);

        service.detectHeavyWriteActivity();

        verify(behaviorRepository).save(any(BehaviorSession.class));
    }

    @Test
    void detectHeavyWriteActivity_doesNotOpenDuplicateSession_whenAlreadyOpen() {
        when(metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2))
                .thenReturn(List.of(snap(110.0), snap(100.0)));   // delta = 10 GB
        when(behaviorRepository.findOpenSessionByType(BehaviorSessionType.HEAVY_WRITE_ACTIVITY))
                .thenReturn(openSession("existing-session"));

        service.detectHeavyWriteActivity();

        verify(behaviorRepository, never()).save(any());
    }

    @Test
    void detectHeavyWriteActivity_savedSession_hasDeviceName() {
        when(metricRepository.findRecentSnapshots(MetricType.SSD_BYTES_WRITTEN_GB, 2))
                .thenReturn(List.of(snap(108.0), snap(100.0)));   // delta = 8.0 GB
        when(behaviorRepository.findOpenSessionByType(BehaviorSessionType.HEAVY_WRITE_ACTIVITY))
                .thenReturn(null);

        service.detectHeavyWriteActivity();

        ArgumentCaptor<BehaviorSession> cap = ArgumentCaptor.forClass(BehaviorSession.class);
        verify(behaviorRepository).save(cap.capture());
        assertThat(cap.getValue().deviceId()).isEqualTo("local-device");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MetricSnapshot snap(double value) {
        return new MetricSnapshot("local-device", MetricType.SSD_BYTES_WRITTEN_GB, value, Instant.now());
    }

    private BehaviorSession openSession(String id) {
        return new BehaviorSession(id, "local-device", BehaviorSessionType.HEAVY_WRITE_ACTIVITY,
                Instant.now().minusSeconds(60), null, "Disk writes +6.0GB");
    }
}

