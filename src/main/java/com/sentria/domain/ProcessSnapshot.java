package com.sentria.domain;

import java.time.Instant;

/**
 * A snapshot of a running OS process captured at a point in time.
 * Used to identify which applications consume the most CPU/RAM over a reporting window.
 */
public record ProcessSnapshot(
        /** Identifier of the device where the process was observed. */
        String deviceId,
        /** Lower-cased process executable name (e.g. "chrome.exe"). */
        String processName,
        /** OS-assigned process ID. */
        int pid,
        /** CPU usage of the process at capture time, 0–100 %. */
        double cpuPercent,
        /** Resident memory used by the process, in megabytes. */
        double memoryMb,
        /** Full command line used to start the process (may be null or empty). */
        String commandLine,
        /** When this snapshot was taken. */
        Instant capturedAt
) {
}
