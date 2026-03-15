package com.sentria.application.monitoring;

import org.springframework.stereotype.Component;

/**
 * Shared mutable status flag that tracks whether SSD monitoring is operational.
 *
 * <p>{@link SsdCollectorService} calls {@link #markAvailable} on successful collection
 * and {@link #markUnavailable} when the required tools are absent or failed.
 * Other services (e.g. {@link com.sentria.finding.SsdFindingService}) read
 * {@link #isAvailable()} before doing SSD-specific analysis.
 */
@Component
public class SsdMonitoringStatus {

    private volatile boolean available = false;
    private volatile String reason = "Pending first scheduled SSD collection";

    /** Returns {@code true} if the last SSD collection attempt succeeded. */
    public boolean isAvailable() {
        return available;
    }

    /** Human-readable explanation of the current status (for diagnostics/logs). */
    public String getReason() {
        return reason;
    }

    /** Called by the collector when SSD data was successfully read. */
    public void markAvailable(String reason) {
        this.available = true;
        this.reason = reason;
    }

    /** Called by the collector when SSD data could not be read. */
    public void markUnavailable(String reason) {
        this.available = false;
        this.reason = reason;
    }
}
