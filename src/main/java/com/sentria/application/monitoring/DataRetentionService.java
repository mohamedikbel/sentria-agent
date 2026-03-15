package com.sentria.application.monitoring;

import com.sentria.config.MonitoringProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically removes old rows from every metric/finding/behavior table.
 *
 * <p>The retention window is driven by {@code monitoring.retention-days} in
 * {@code application.properties}. The cleanup job runs once every 24 hours
 * (fixed delay) and logs the number of rows deleted per table.
 */
@Slf4j
@Service
@DependsOn("flyway")
@RequiredArgsConstructor
public class DataRetentionService {

    private final JdbcTemplate jdbcTemplate;
    private final MonitoringProperties monitoringProperties;

    /** Deletes all rows older than the configured retention window across all tables. */
    @Scheduled(fixedDelay = 86_400_000)
    public void cleanupOldData() {
        int retentionDays = Math.max(1, monitoringProperties.retentionDays());
        Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        int metricDeleted   = deleteFrom("metric_snapshot",  "captured_at", threshold);
        int findingDeleted  = deleteFrom("finding",          "created_at",  threshold);
        int processDeleted  = deleteFrom("process_snapshot", "captured_at", threshold);
        int behaviorDeleted = deleteFrom("behavior_session", "started_at",  threshold);

        int total = metricDeleted + findingDeleted + processDeleted + behaviorDeleted;
        if (total > 0) {
            log.info("Retention cleanup removed {} row(s): metric={}, finding={}, process={}, behavior={} (threshold={})",
                    total, metricDeleted, findingDeleted, processDeleted, behaviorDeleted, threshold);
        }
    }

    /** Executes a DELETE on {@code table} for rows whose {@code dateColumn} predates {@code threshold}. */
    private int deleteFrom(String table, String dateColumn, Instant threshold) {
        String sql = "DELETE FROM " + table + " WHERE " + dateColumn + " < ?";
        return jdbcTemplate.update(sql, threshold.toString());
    }
}
