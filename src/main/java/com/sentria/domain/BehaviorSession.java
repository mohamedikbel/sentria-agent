package com.sentria.domain;

import java.time.Instant;

/**
 * Represents a detected user activity session (e.g. gaming, video editing).
 * Sessions have a start time and an optional end time; an open session has a null {@code endedAt}.
 */
public record BehaviorSession(
        /** Unique session identifier (UUID). */
        String id,
        /** Identifier of the device where the session was detected. */
        String deviceId,
        /** Category of behavior that was observed. */
        BehaviorSessionType sessionType,
        /** When the session started. */
        Instant startedAt,
        /** When the session ended, or null if still open. */
        Instant endedAt,
        /** Free-text context, typically the name of the triggering process. */
        String context
) {
}
