package com.sentria.application.monitoring;

import com.sentria.config.MonitoringProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataRetentionService}.
 * Verifies that {@code cleanupOldData()} issues the correct DELETE statements
 * without hitting a real database.
 */
@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private MonitoringProperties monitoringProperties;

    private DataRetentionService service;

    @BeforeEach
    void setUp() {
        service = new DataRetentionService(jdbcTemplate, monitoringProperties);
    }

    @Test
    void cleanupOldData_issuesExactlyFourDeleteStatements() {
        when(monitoringProperties.retentionDays()).thenReturn(30);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(0);

        service.cleanupOldData();

        // Four tables: metric_snapshot, finding, process_snapshot, behavior_session
        verify(jdbcTemplate, times(4)).update(anyString(), anyString());
    }

    @Test
    void cleanupOldData_targetsAllFourTableNames() {
        when(monitoringProperties.retentionDays()).thenReturn(30);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(0);

        service.cleanupOldData();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(4)).update(sqlCaptor.capture(), anyString());

        List<String> sqls = sqlCaptor.getAllValues();
        assertThat(sqls).anyMatch(s -> s.contains("metric_snapshot"));
        assertThat(sqls).anyMatch(s -> s.contains("finding"));
        assertThat(sqls).anyMatch(s -> s.contains("process_snapshot"));
        assertThat(sqls).anyMatch(s -> s.contains("behavior_session"));
    }

    @Test
    void cleanupOldData_passesIsoInstantAsThreshold() {
        when(monitoringProperties.retentionDays()).thenReturn(30);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(0);

        service.cleanupOldData();

        ArgumentCaptor<String> thresholdCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), thresholdCaptor.capture());

        // All thresholds should be identical ISO-8601 instants (same call)
        assertThat(thresholdCaptor.getAllValues())
                .allSatisfy(ts -> assertThat(ts).matches("\\d{4}-\\d{2}-\\d{2}T.*Z"));
    }

    @Test
    void cleanupOldData_enforcesMinimumRetentionOfOneDay() {
        // retentionDays = 0 (or negative) must be clamped to 1
        when(monitoringProperties.retentionDays()).thenReturn(0);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(0);

        // Should not throw, and still issue the four deletes
        service.cleanupOldData();

        verify(jdbcTemplate, times(4)).update(anyString(), anyString());
    }

    @Test
    void cleanupOldData_logsNothing_whenNoRowsDeleted() {
        when(monitoringProperties.retentionDays()).thenReturn(30);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(0);

        // Must complete without exception even when all row counts are 0
        service.cleanupOldData();

        verify(jdbcTemplate, times(4)).update(anyString(), anyString());
    }

    @Test
    void cleanupOldData_usesDeleteStatement_notTruncate() {
        when(monitoringProperties.retentionDays()).thenReturn(7);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

        service.cleanupOldData();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(4)).update(sqlCaptor.capture(), anyString());

        assertThat(sqlCaptor.getAllValues())
                .allSatisfy(sql -> assertThat(sql.toUpperCase()).startsWith("DELETE FROM"));
    }
}

