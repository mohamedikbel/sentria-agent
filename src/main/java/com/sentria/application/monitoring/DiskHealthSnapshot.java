package com.sentria.application.monitoring;

import java.time.Instant;
import java.util.Map;

public record DiskHealthSnapshot(
        String diskId,
        String model,
        String serial,
        Long sizeBytes,
        Double healthPercent,
        Double temperatureC,
        Double bytesWrittenGb,
        Long reads,
        Long writes,
        Instant capturedAt,
        Map<String, String> metadata
) {
}



