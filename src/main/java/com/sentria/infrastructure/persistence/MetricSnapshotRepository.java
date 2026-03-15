package com.sentria.infrastructure.persistence;

import com.sentria.application.port.MetricSnapshotStore;
import com.sentria.domain.MetricSnapshot;
import com.sentria.domain.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MetricSnapshotRepository implements MetricSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(MetricSnapshot snapshot) {
        String sql = """
                INSERT INTO metric_snapshot (
                    device_id,
                    metric_type,
                    metric_value,
                    captured_at
                ) VALUES (?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                snapshot.deviceId(),
                snapshot.metricType().name(),
                snapshot.value(),
                snapshot.capturedAt().toString()
        );
    }

    @Override
    public MetricSnapshot findLatestByType(MetricType metricType) {
        String sql = """
                SELECT device_id, metric_type, metric_value, captured_at
                FROM metric_snapshot
                WHERE metric_type = ?
                ORDER BY captured_at DESC
                LIMIT 1
                """;

        List<MetricSnapshot> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MetricSnapshot(
                        rs.getString("device_id"),
                        MetricType.valueOf(rs.getString("metric_type")),
                        rs.getDouble("metric_value"),
                        Instant.parse(rs.getString("captured_at"))
                ),
                metricType.name()
        );

        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<MetricSnapshot> findByTypeSince(MetricType type, Instant since) {

        String sql = """
        SELECT device_id, metric_type, metric_value, captured_at
        FROM metric_snapshot
        WHERE metric_type = ?
        AND captured_at >= ?
        ORDER BY captured_at ASC
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MetricSnapshot(
                        rs.getString("device_id"),
                        MetricType.valueOf(rs.getString("metric_type")),
                        rs.getDouble("metric_value"),
                        Instant.parse(rs.getString("captured_at"))
                ),
                type.name(),
                since.toString()
        );
    }

    @Override
    public List<MetricSnapshot> findRecentSnapshots(MetricType type, int limit) {

        String sql = """
        SELECT device_id, metric_type, metric_value, captured_at
        FROM metric_snapshot
        WHERE metric_type = ?
        ORDER BY captured_at DESC
        LIMIT ?
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MetricSnapshot(
                        rs.getString("device_id"),
                        MetricType.valueOf(rs.getString("metric_type")),
                        rs.getDouble("metric_value"),
                        Instant.parse(rs.getString("captured_at"))
                ),
                type.name(),
                limit
        );
    }
}
