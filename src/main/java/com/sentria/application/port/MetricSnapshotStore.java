package com.sentria.application.port;

import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;

import java.time.Instant;
import java.util.List;

/**
 * Port for persisting and querying {@link MetricSnapshot} objects.
 * Implementations are located in the infrastructure layer.
 */
public interface MetricSnapshotStore {

    /** Persists a single snapshot. */
    void save(MetricSnapshot snapshot);

    /** Returns the most recently captured snapshot for the given metric type, or null if none exists. */
    MetricSnapshot findLatestByType(MetricType metricType);

    /** Returns all snapshots of the given type captured after {@code since}, ordered oldest-first. */
    List<MetricSnapshot> findByTypeSince(MetricType type, Instant since);

    /** Returns the {@code limit} most recent snapshots of the given type, newest-first. */
    List<MetricSnapshot> findRecentSnapshots(MetricType type, int limit);
}
