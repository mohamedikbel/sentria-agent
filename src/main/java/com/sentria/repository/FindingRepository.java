package com.sentria.repository;


import com.sentria.domain.Finding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FindingRepository {

    private final JdbcTemplate jdbcTemplate;

    public FindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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
}
