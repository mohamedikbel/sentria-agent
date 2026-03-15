package com.sentria.collector;

import com.sentria.application.monitoring.MetricCollectionOrchestrator;
import com.sentria.application.monitoring.SsdMonitoringStatus;
import com.sentria.domain.MetricType;
import com.sentria.infrastructure.persistence.MetricSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SsdCollectionIntegrationTest {

    @Autowired
    private MetricCollectionOrchestrator metricCollectionOrchestrator;

    @Autowired
    private MetricSnapshotRepository metricSnapshotRepository;

    @Autowired
    private SsdMonitoringStatus ssdMonitoringStatus;

    @Test
    void collectSsdMetrics_savesAtLeastOneSsdMetricAndUpdatesStatus() {
        metricCollectionOrchestrator.collectOnce();

        boolean hasHealth = metricSnapshotRepository.findLatestByType(MetricType.SSD_HEALTH_PERCENT) != null;
        boolean hasWritten = metricSnapshotRepository.findLatestByType(MetricType.SSD_BYTES_WRITTEN_GB) != null;

        assertTrue(hasHealth || hasWritten,
                "Expected at least one SSD metric persisted (health or bytes written)");
        assertTrue(ssdMonitoringStatus.isAvailable(),
                "SSD monitoring status should be available after successful collection");
    }
}



