package com.sentria.domain;

import java.time.Instant;
import java.util.List;

/**
 * Represents an anomaly detected on the device (e.g. battery fully charged, SSD wear).
 * A Finding bundles the observed facts, severity level, confidence,
 * a likely contributor, and actionable recommendations for the user.
 */
public record Finding(

        /** Unique identifier for this finding (UUID). */
        String id,

        /** Finding type – one of the constants defined in {@link FindingType}. */
        String type,

        /** How serious the issue is: LOW, MEDIUM, HIGH or CRITICAL. */
        Severity severity,

        /** How confident the analysis is: LOW, MEDIUM or HIGH. */
        Confidence confidence,

        /** Raw facts that justify this finding. */
        List<String> facts,

        /** Process or activity identified as the probable cause (may be null). */
        String likelyContributor,

        /** Recommended actions for the user to take. */
        List<String> recommendations,

        /** Timestamp when this finding was created. */
        Instant createdAt

) {
}