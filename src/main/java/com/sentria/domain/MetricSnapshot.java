package com.sentria.domain;

import java.time.Instant;

public record MetricSnapshot(

        String deviceId,

        MetricType metricType,

        double value,

        Instant capturedAt

) {
}