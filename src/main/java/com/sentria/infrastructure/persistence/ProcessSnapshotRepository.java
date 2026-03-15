package com.sentria.infrastructure.persistence;

import com.sentria.application.port.ProcessSnapshotStore;
import com.sentria.domain.ProcessSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProcessSnapshotRepository implements ProcessSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveAll(List<ProcessSnapshot> snapshots) {
        String sql = """
                INSERT INTO process_snapshot (
                    device_id,
                    process_name,
                    pid,
                    cpu_percent,
                    memory_mb,
                    command_line,
                    captured_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        for (ProcessSnapshot snapshot : snapshots) {
            jdbcTemplate.update(
                    sql,
                    snapshot.deviceId(),
                    snapshot.processName(),
                    snapshot.pid(),
                    snapshot.cpuPercent(),
                    snapshot.memoryMb(),
                    snapshot.commandLine(),
                    snapshot.capturedAt().toString()
            );
        }
    }

    @Override
    public List<ProcessSnapshot> findTopByCpuSince(Instant since, int limit) {
        String sql = """
                SELECT device_id, process_name, pid, cpu_percent, memory_mb, command_line, captured_at
                FROM process_snapshot
                WHERE captured_at >= ?
                ORDER BY cpu_percent DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProcessSnapshot(
                        rs.getString("device_id"),
                        rs.getString("process_name"),
                        rs.getInt("pid"),
                        rs.getDouble("cpu_percent"),
                        rs.getDouble("memory_mb"),
                        rs.getString("command_line"),
                        Instant.parse(rs.getString("captured_at"))
                ),
                since.toString(),
                limit
        );
    }

    @Override
    public List<ProcessSnapshot> findMostUsedSince(Instant since, int limit) {
        String sql = """
                SELECT
                    COALESCE(MAX(device_id), 'local-device') AS device_id,
                    process_name,
                    COALESCE(MAX(pid), 0) AS pid,
                    AVG(cpu_percent) AS cpu_percent,
                    AVG(memory_mb) AS memory_mb,
                    COALESCE(MAX(command_line), '') AS command_line,
                    COALESCE(MAX(captured_at), ?) AS captured_at
                FROM process_snapshot
                WHERE captured_at >= ?
                GROUP BY process_name
                ORDER BY AVG(cpu_percent) DESC, COUNT(*) DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProcessSnapshot(
                        rs.getString("device_id"),
                        rs.getString("process_name"),
                        rs.getInt("pid"),
                        rs.getDouble("cpu_percent"),
                        rs.getDouble("memory_mb"),
                        rs.getString("command_line"),
                        Instant.parse(rs.getString("captured_at"))
                ),
                since.toString(),
                since.toString(),
                limit
        );
    }
}


