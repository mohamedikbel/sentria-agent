package com.sentria.application.monitoring;

import com.sentria.domain.MetricSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * Plug-in contract for metric collectors.
 *
 * To add a new metric source, create a new @Service implementing this interface.
 * The orchestrator will discover it automatically and persist returned snapshots.
 */
public interface MetricCollectorPlugin {

    String collectorName();

    List<MetricSnapshot> collect(Instant collectedAt);
}



