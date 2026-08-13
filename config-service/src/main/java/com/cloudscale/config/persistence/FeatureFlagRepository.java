package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.FeatureFlag;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FeatureFlagRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureFlagRowMapper rowMapper = new FeatureFlagRowMapper();

    public FeatureFlagRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(FeatureFlag featureFlag) {
        String sql = """
                INSERT INTO feature_flags
                    (id, environment_id, feature_key, name, description, enabled, published_version_id,
                     revision, created_at, updated_at)
                VALUES (:id, :environmentId, :key, :name, :description, :enabled, :publishedVersionId,
                        :revision, :createdAt, :updatedAt)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", featureFlag.id())
                .addValue("environmentId", featureFlag.environmentId())
                .addValue("key", featureFlag.key())
                .addValue("name", featureFlag.name())
                .addValue("description", featureFlag.description())
                .addValue("enabled", featureFlag.enabled())
                .addValue("publishedVersionId", featureFlag.publishedVersionId())
                .addValue("revision", featureFlag.revision())
                .addValue("createdAt", Timestamp.from(featureFlag.createdAt()))
                .addValue("updatedAt", Timestamp.from(featureFlag.updatedAt())));
    }

    public Optional<FeatureFlag> findById(UUID id) {
        String sql = "SELECT * FROM feature_flags WHERE id = :id";
        return jdbc.query(sql, new MapSqlParameterSource("id", id), rowMapper).stream().findFirst();
    }

    public Optional<FeatureFlag> lockById(UUID id) {
        String sql = "SELECT * FROM feature_flags WHERE id = :id FOR UPDATE";
        return jdbc.query(sql, new MapSqlParameterSource("id", id), rowMapper).stream().findFirst();
    }

    public Optional<FeatureFlag> findByEnvironmentAndKey(UUID environmentId, String key) {
        String sql = "SELECT * FROM feature_flags WHERE environment_id = :environmentId AND feature_key = :key";
        return jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("environmentId", environmentId)
                        .addValue("key", key), rowMapper)
                .stream().findFirst();
    }

    public List<FeatureFlag> findAllByEnvironment(UUID environmentId) {
        String sql = "SELECT * FROM feature_flags WHERE environment_id = :environmentId ORDER BY feature_key LIMIT 100";
        return jdbc.query(sql, new MapSqlParameterSource("environmentId", environmentId), rowMapper);
    }

    public FeatureFlag updateMetadata(UUID id, String name, String description, Boolean enabled, Instant updatedAt) {
        String sql = """
                UPDATE feature_flags
                SET name = COALESCE(:name, name),
                    description = COALESCE(:description, description),
                    enabled = COALESCE(:enabled, enabled),
                    revision = revision + 1,
                    updated_at = :updatedAt
                WHERE id = :id
                """;
        int updated = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("enabled", enabled)
                .addValue("updatedAt", Timestamp.from(updatedAt)));
        if (updated == 0) {
            throw new IllegalStateException("Feature flag disappeared during update");
        }
        return findById(id).orElseThrow();
    }

    public int setPublishedVersion(UUID featureFlagId, UUID versionId, Instant updatedAt) {
        String sql = """
                UPDATE feature_flags
                SET published_version_id = :versionId,
                    revision = revision + 1,
                    updated_at = :updatedAt
                WHERE id = :featureFlagId
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("featureFlagId", featureFlagId)
                .addValue("versionId", versionId)
                .addValue("updatedAt", Timestamp.from(updatedAt)));
    }
}
