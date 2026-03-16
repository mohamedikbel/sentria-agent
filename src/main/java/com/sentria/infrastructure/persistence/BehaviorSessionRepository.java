package com.sentria.infrastructure.persistence;

import com.sentria.application.port.BehaviorSessionStore;
import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BehaviorSessionRepository implements BehaviorSessionStore {

    private static final String COL_ID           = "id";
    private static final String COL_DEVICE_ID    = "device_id";
    private static final String COL_SESSION_TYPE = "session_type";
    private static final String COL_STARTED_AT   = "started_at";
    private static final String COL_ENDED_AT     = "ended_at";
    private static final String COL_CONTEXT      = "context";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(BehaviorSession session) {
        String sql = """
                INSERT INTO behavior_session (
                    id,
                    device_id,
                    session_type,
                    started_at,
                    ended_at,
                    context
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                session.id(),
                session.deviceId(),
                session.sessionType().name(),
                session.startedAt().toString(),
                session.endedAt() != null ? session.endedAt().toString() : null,
                session.context()
        );
    }

    @Override
    public BehaviorSession findOpenSessionByType(BehaviorSessionType type) {
        String sql = """
                SELECT id, device_id, session_type, started_at, ended_at, context
                FROM behavior_session
                WHERE session_type = ?
                  AND ended_at IS NULL
                ORDER BY started_at DESC
                LIMIT 1
                """;

        List<BehaviorSession> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new BehaviorSession(
                        rs.getString(COL_ID),
                        rs.getString(COL_DEVICE_ID),
                        BehaviorSessionType.valueOf(rs.getString(COL_SESSION_TYPE)),
                        Instant.parse(rs.getString(COL_STARTED_AT)),
                        rs.getString(COL_ENDED_AT) != null ? Instant.parse(rs.getString(COL_ENDED_AT)) : null,
                        rs.getString(COL_CONTEXT)
                ),
                type.name()
        );

        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void closeSession(String sessionId, Instant endedAt) {
        String sql = """
                UPDATE behavior_session
                SET ended_at = ?
                WHERE id = ?
                """;

        jdbcTemplate.update(sql, endedAt.toString(), sessionId);
    }

    @Override
    public List<BehaviorSession> findSessionsByTypeSince(BehaviorSessionType type, Instant since) {
        String sql = """
            SELECT id, device_id, session_type, started_at, ended_at, context
            FROM behavior_session
            WHERE session_type = ?
              AND started_at >= ?
            ORDER BY started_at ASC
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new BehaviorSession(
                        rs.getString(COL_ID),
                        rs.getString(COL_DEVICE_ID),
                        BehaviorSessionType.valueOf(rs.getString(COL_SESSION_TYPE)),
                        Instant.parse(rs.getString(COL_STARTED_AT)),
                        rs.getString(COL_ENDED_AT) != null ? Instant.parse(rs.getString(COL_ENDED_AT)) : null,
                        rs.getString(COL_CONTEXT)
                ),
                type.name(),
                since.toString()
        );
    }

    @Override
    public List<BehaviorSession> findSessionsSince(Instant since) {
        String sql = """
            SELECT id, device_id, session_type, started_at, ended_at, context
            FROM behavior_session
            WHERE started_at >= ?
            ORDER BY started_at ASC
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new BehaviorSession(
                        rs.getString(COL_ID),
                        rs.getString(COL_DEVICE_ID),
                        BehaviorSessionType.valueOf(rs.getString(COL_SESSION_TYPE)),
                        Instant.parse(rs.getString(COL_STARTED_AT)),
                        rs.getString(COL_ENDED_AT) != null ? Instant.parse(rs.getString(COL_ENDED_AT)) : null,
                        rs.getString(COL_CONTEXT)
                ),
                since.toString()
        );
    }
}
