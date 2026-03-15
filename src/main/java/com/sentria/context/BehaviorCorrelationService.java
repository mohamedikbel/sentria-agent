package com.sentria.context;

import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import com.sentria.application.port.BehaviorSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Correlates SSD heavy-write activity sessions with higher-level behaviors
 * (video editing, gaming) to explain why SSD wear accelerated.
 *
 * <p>The logic counts how many heavy-write sessions temporally overlap with
 * video-editing or gaming sessions. The category with more overlaps is reported
 * as the likely contributor.
 */
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class BehaviorCorrelationService {

    private final BehaviorSessionStore behaviorSessionRepository;

    /**
     * Analyses behavior sessions since {@code since} and returns a human-readable
     * explanation of the most likely SSD wear contributor, or {@code null} if no
     * heavy-write sessions were recorded.
     *
     * @param since start of the analysis window (typically 14 days ago)
     * @return a descriptive string or {@code null}
     */
    public String findLikelySsdWearContributor(Instant since) {
        List<BehaviorSession> heavyWrites =
                behaviorSessionRepository.findSessionsByTypeSince(BehaviorSessionType.HEAVY_WRITE_ACTIVITY, since);

        // No heavy-write activity in the window → nothing to correlate.
        if (heavyWrites.isEmpty()) {
            return null;
        }

        List<BehaviorSession> videoEditing =
                behaviorSessionRepository.findSessionsByTypeSince(BehaviorSessionType.VIDEO_EDITING, since);

        List<BehaviorSession> gaming =
                behaviorSessionRepository.findSessionsByTypeSince(BehaviorSessionType.GAMING, since);

        long videoMatches  = countOverlaps(heavyWrites, videoEditing);
        long gamingMatches = countOverlaps(heavyWrites, gaming);

        if (videoMatches == 0 && gamingMatches == 0) {
            return "Heavy disk write activity was detected, but no dominant workload pattern was identified";
        }

        if (videoMatches >= gamingMatches) {
            return "Heavy disk write activity was repeatedly detected during video editing sessions";
        }

        return "Heavy disk write activity was repeatedly detected during gaming-related sessions";
    }

    /**
     * Counts how many sessions in {@code left} overlap in time with at least
     * one session in {@code right}.
     */
    private long countOverlaps(List<BehaviorSession> left, List<BehaviorSession> right) {
        return left.stream()
                .filter(l -> right.stream().anyMatch(r -> overlaps(l, r)))
                .count();
    }

    /**
     * Returns {@code true} when sessions {@code a} and {@code b} share at least one
     * instant in time. An open session (null {@code endedAt}) is treated as ongoing.
     */
    private boolean overlaps(BehaviorSession a, BehaviorSession b) {
        Instant aEnd = a.endedAt() != null ? a.endedAt() : Instant.now();
        Instant bEnd = b.endedAt() != null ? b.endedAt() : Instant.now();

        return !a.startedAt().isAfter(bEnd) && !b.startedAt().isAfter(aEnd);
    }
}