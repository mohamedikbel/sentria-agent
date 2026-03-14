package com.sentria.repository;

import com.sentria.domain.BehaviorSession;
import com.sentria.domain.BehaviorSessionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BehaviorSessionRepository {

    private final JdbcTemplate jdbcTemplate;

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
                        rs.getString("id"),
                        rs.getString("device_id"),
                        BehaviorSessionType.valueOf(rs.getString("session_type")),
                        Instant.parse(rs.getString("started_at")),
                        rs.getString("ended_at") != null ? Instant.parse(rs.getString("ended_at")) : null,
                        rs.getString("context")
                ),
                type.name()
        );

        return results.isEmpty() ? null : results.get(0);
    }

    public void closeSession(String sessionId, Instant endedAt) {
        String sql = """
                UPDATE behavior_session
                SET ended_at = ?
                WHERE id = ?
                """;

        jdbcTemplate.update(sql, endedAt.toString(), sessionId);
    }

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
                        rs.getString("id"),
                        rs.getString("device_id"),
                        BehaviorSessionType.valueOf(rs.getString("session_type")),
                        Instant.parse(rs.getString("started_at")),
                        rs.getString("ended_at") != null ? Instant.parse(rs.getString("ended_at")) : null,
                        rs.getString("context")
                ),
                type.name(),
                since.toString()
        );
    }

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
                        rs.getString("id"),
                        rs.getString("device_id"),
                        BehaviorSessionType.valueOf(rs.getString("session_type")),
                        Instant.parse(rs.getString("started_at")),
                        rs.getString("ended_at") != null ? Instant.parse(rs.getString("ended_at")) : null,
                        rs.getString("context")
                ),
                since.toString()
        );
    }
}