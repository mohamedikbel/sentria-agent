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
