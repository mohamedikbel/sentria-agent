package com.sentria.application.port;

import com.sentria.domain.Finding;

import java.time.Instant;
import java.util.List;

/**
 * Port (output interface) for persisting and querying {@link Finding} objects.
 * Implementations are located in the infrastructure layer.
 */
public interface FindingStore {

    /** Persists a new finding. */
    void save(Finding finding);

    /**
     * Returns {@code true} if a finding of the given type was already saved
     * after {@code sinceIsoInstant} (ISO-8601 string), preventing duplicate alerts.
     */
    boolean existsByTypeSince(String type, String sinceIsoInstant);

    /** Returns the most recent findings created after {@code since}, up to {@code limit} results. */
    List<Finding> findRecentSince(Instant since, int limit);
}
