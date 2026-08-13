package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.TargetingRule;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;

@Repository
public class TargetingRuleRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final TargetingRuleRowMapper rowMapper;
    private final JsonDocumentMapper jsonDocumentMapper;

    public TargetingRuleRepository(NamedParameterJdbcTemplate jdbc, JsonDocumentMapper jsonDocumentMapper) {
        this.jdbc = jdbc;
        this.jsonDocumentMapper = jsonDocumentMapper;
        this.rowMapper = new TargetingRuleRowMapper(jsonDocumentMapper);
    }

    public void insert(TargetingRule rule) {
        String sql = """
                INSERT INTO targeting_rules (id, feature_version_id, priority, conditions, value, created_at)
                VALUES (:id, :featureVersionId, :priority, CAST(:conditions AS jsonb), CAST(:value AS jsonb), :createdAt)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", rule.id())
                .addValue("featureVersionId", rule.featureVersionId())
                .addValue("priority", rule.priority())
                .addValue("conditions", jsonDocumentMapper.write(rule.conditions()))
                .addValue("value", jsonDocumentMapper.write(rule.value()))
                .addValue("createdAt", Timestamp.from(rule.createdAt())));
    }

    public List<TargetingRule> findAllByVersion(UUID featureVersionId) {
        String sql = """
                SELECT * FROM targeting_rules
                WHERE feature_version_id = :featureVersionId
                ORDER BY priority
                LIMIT 100
                """;
        return jdbc.query(sql, new MapSqlParameterSource("featureVersionId", featureVersionId), rowMapper);
    }
}
