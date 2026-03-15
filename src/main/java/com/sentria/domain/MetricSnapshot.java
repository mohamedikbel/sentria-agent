package com.sentria.domain;

import java.time.Instant;

/**
 * A point-in-time snapshot of a system metric (CPU, RAM, battery, SSD…).
 * Snapshots are persisted and used for trend analysis and anomaly detection.
 */
public record MetricSnapshot(

        /** Identifier of the source device (e.g. "local-device"). */
        String deviceId,

        /** The type of metric that was collected. */
        MetricType metricType,

        /** Numeric value of the metric (unit depends on the type). */
        double value,

        /** When the snapshot was captured. */
        Instant capturedAt

) {
}