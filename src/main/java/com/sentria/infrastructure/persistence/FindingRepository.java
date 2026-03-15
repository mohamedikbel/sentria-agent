package com.sentria.infrastructure.persistence;


import com.sentria.application.port.FindingStore;
import com.sentria.domain.Confidence;
import com.sentria.domain.Finding;
import com.sentria.domain.Severity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Repository
public class FindingRepository implements FindingStore {

    private final JdbcTemplate jdbcTemplate;

    public FindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Finding finding) {
        String sql = """
                INSERT INTO finding (
                    id,
                    type,
                    severity,
                    confidence,
                    facts,
                    likely_contributor,
                    recommendations,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                finding.id(),
                finding.type(),
                finding.severity().name(),
                finding.confidence().name(),
                String.join(" | ", finding.facts()),
                finding.likelyContributor(),
                String.join(" | ", finding.recommendations()),
                finding.createdAt().toString()
        );
    }

    @Override
    public boolean existsByTypeSince(String type, String sinceIsoInstant) {
        String sql = """
                SELECT COUNT(*)
                FROM finding
                WHERE type = ?
                  AND created_at >= ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, type, sinceIsoInstant);
        return count != null && count > 0;
    }

    @Override
    public List<Finding> findRecentSince(Instant since, int limit) {
        String sql = """
                SELECT id, type, severity, confidence, facts, likely_contributor, recommendations, created_at
                FROM finding
                WHERE created_at >= ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Finding(
                        rs.getString("id"),
                        rs.getString("type"),
                        Severity.valueOf(rs.getString("severity")),
                        Confidence.valueOf(rs.getString("confidence")),
                        splitPipe(rs.getString("facts")),
                        rs.getString("likely_contributor"),
                        splitPipe(rs.getString("recommendations")),
                        Instant.parse(rs.getString("created_at"))
                ),
                since.toString(),
                Math.max(1, limit)
        );
    }

    private List<String> splitPipe(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split("\\\\s*\\|\\\\s*"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}


