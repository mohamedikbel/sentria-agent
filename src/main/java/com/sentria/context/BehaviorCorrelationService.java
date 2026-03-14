package com.sentria.context;


import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import com.sentria.repository.BehaviorSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@DependsOn("flyway")

@RequiredArgsConstructor
public class BehaviorCorrelationService {

    private final BehaviorSessionRepository behaviorSessionRepository;

    public String findLikelySsdWearContributor(Instant since) {
        List<BehaviorSession> heavyWrites =
                behaviorSessionRepository.findSessionsByTypeSince(BehaviorSessionType.HEAVY_WRITE_ACTIVITY, since);

        if (heavyWrites.isEmpty()) {
            return null;
        }

        List<BehaviorSession> videoEditing =
                behaviorSessionRepository.findSessionsByTypeSince(BehaviorSessionType.VIDEO_EDITING, since);

        List<BehaviorSession> gaming =
                behaviorSessionRepository.findSessionsByTypeSince(BehaviorSessionType.GAMING, since);

        long videoMatches = countOverlaps(heavyWrites, videoEditing);
        long gamingMatches = countOverlaps(heavyWrites, gaming);

        if (videoMatches == 0 && gamingMatches == 0) {
            return "Heavy disk write activity was detected, but no dominant workload pattern was identified";
        }

        if (videoMatches >= gamingMatches) {
            return "Heavy disk write activity was repeatedly detected during video editing sessions";
        }

        return "Heavy disk write activity was repeatedly detected during gaming-related sessions";
    }

    private long countOverlaps(List<BehaviorSession> left, List<BehaviorSession> right) {
        return left.stream()
                .filter(l -> right.stream().anyMatch(r -> overlaps(l, r)))
                .count();
    }

    private boolean overlaps(BehaviorSession a, BehaviorSession b) {
        Instant aEnd = a.endedAt() != null ? a.endedAt() : Instant.now();
        Instant bEnd = b.endedAt() != null ? b.endedAt() : Instant.now();

        return !a.startedAt().isAfter(bEnd) && !b.startedAt().isAfter(aEnd);
    }
}