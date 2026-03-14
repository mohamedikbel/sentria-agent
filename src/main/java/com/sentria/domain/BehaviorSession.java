package com.sentria.domain;


import java.time.Instant;

public record BehaviorSession(
        String id,
        String deviceId,
        BehaviorSessionType sessionType,
        Instant startedAt,
        Instant endedAt,
        String context
) {
}
