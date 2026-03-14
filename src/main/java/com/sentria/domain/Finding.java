package com.sentria.domain;

import java.time.Instant;
import java.util.List;

public record Finding(

        String id,

        String type,

        Severity severity,

        Confidence confidence,

        List<String> facts,

        String likelyContributor,

        List<String> recommendations,

        Instant createdAt

) {
}