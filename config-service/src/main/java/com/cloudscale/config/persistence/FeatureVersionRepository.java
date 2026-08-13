package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.FeatureVersion;
import com.cloudscale.config.domain.VersionStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FeatureVersionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureVersionRowMapper rowMapper;

    public FeatureVersionRepository(NamedParameterJdbcTemplate jdbc, JsonDocumentMapper jsonDocumentMapper) {
        this.jdbc = jdbc;
        this.rowMapper = new FeatureVersionRowMapper(jsonDocumentMapper);
    }

    public void insert(FeatureVersion version) {
        String sql = """
                INSERT INTO feature_versions
                    (id, feature_flag_id, version_number, status, value, created_by, created_at)
                VALUES (:id, :featureFlagId, :versionNumber, :status, CAST(:value AS jsonb), :createdBy, :createdAt)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", version.id())
                .addValue("featureFlagId", version.featureFlagId())
                .addValue("versionNumber", version.versionNumber())
                .addValue("status", version.status().name())
                .addValue("value", version.value().toString())
                .addValue("createdBy", version.createdBy())
                .addValue("createdAt", Timestamp.from(version.createdAt())));
    }

    public Optional<FeatureVersion> findById(UUID id) {
        String sql = "SELECT * FROM feature_versions WHERE id = :id";
        return jdbc.query(sql, new MapSqlParameterSource("id", id), rowMapper).stream().findFirst();
    }

    public Optional<FeatureVersion> findByFeatureAndNumber(UUID featureFlagId, int versionNumber) {
        String sql = """
                SELECT * FROM feature_versions
                WHERE feature_flag_id = :featureFlagId AND version_number = :versionNumber
                """;
        return jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("featureFlagId", featureFlagId)
                        .addValue("versionNumber", versionNumber), rowMapper)
                .stream().findFirst();
    }

    public Optional<FeatureVersion> findDraft(UUID featureFlagId) {
        String sql = "SELECT * FROM feature_versions WHERE feature_flag_id = :featureFlagId AND status = 'DRAFT'";
        return jdbc.query(sql, new MapSqlParameterSource("featureFlagId", featureFlagId), rowMapper)
                .stream().findFirst();
    }

    public List<FeatureVersion> findAllByFeature(UUID featureFlagId) {
        String sql = """
                SELECT * FROM feature_versions
                WHERE feature_flag_id = :featureFlagId
                ORDER BY version_number DESC
                LIMIT 100
                """;
        return jdbc.query(sql, new MapSqlParameterSource("featureFlagId", featureFlagId), rowMapper);
    }

    public int findNextVersionNumber(UUID featureFlagId) {
        String sql = "SELECT COALESCE(MAX(version_number), 0) + 1 FROM feature_versions WHERE feature_flag_id = :featureFlagId";
        return jdbc.queryForObject(sql, new MapSqlParameterSource("featureFlagId", featureFlagId), Integer.class);
    }

    public int updateStatus(UUID id, VersionStatus status) {
        String sql = "UPDATE feature_versions SET status = :status WHERE id = :id";
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name()));
    }
}
