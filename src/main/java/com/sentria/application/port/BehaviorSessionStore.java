package com.sentria.application.port;

import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;

import java.time.Instant;
import java.util.List;

/**
 * Port for persisting and querying {@link BehaviorSession} objects.
 * Implementations are located in the infrastructure layer.
 */
public interface BehaviorSessionStore {

    /** Persists a new behavior session. */
    void save(BehaviorSession session);

    /**
     * Returns the currently open (not yet closed) session for the given type,
     * or {@code null} if no such session exists.
     */
    BehaviorSession findOpenSessionByType(BehaviorSessionType type);

    /** Closes the session identified by {@code sessionId} by setting its end time. */
    void closeSession(String sessionId, Instant endedAt);

    /** Returns all sessions of the given type that started after {@code since}. */
    List<BehaviorSession> findSessionsByTypeSince(BehaviorSessionType type, Instant since);

    /** Returns all sessions (any type) that started after {@code since}. */
    List<BehaviorSession> findSessionsSince(Instant since);
}
