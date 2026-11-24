package com.cloudscale.rollout.persistence;

import com.cloudscale.rollout.domain.HealthMetric;
import com.cloudscale.rollout.domain.HealthObservation;
import com.cloudscale.rollout.domain.Rollout;
import com.cloudscale.rollout.domain.RolloutHealthPolicy;
import com.cloudscale.rollout.domain.RolloutStage;
import com.cloudscale.rollout.domain.RolloutStageStatus;
import com.cloudscale.rollout.domain.RolloutStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RolloutRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public RolloutRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Rollout rollout, List<RolloutStage> stages, RolloutHealthPolicy policy) {
        jdbc.update("""
                INSERT INTO rollouts
                    (id, organization_id, feature_flag_id, environment_id, status, current_percentage,
                     created_at, updated_at, last_auto_paused_at)
                VALUES (:id, :organizationId, :featureFlagId, :environmentId, :status, :currentPercentage,
                        :createdAt, :updatedAt, :lastAutoPausedAt)
                """, rolloutParameters(rollout));
        for (RolloutStage stage : stages) {
            insertStage(stage);
        }
        if (policy != null) {
            insertHealthPolicy(UUID.randomUUID(), rollout.id(), policy, rollout.createdAt());
        }
    }

    public Optional<Rollout> findByOrganizationAndId(UUID organizationId, UUID rolloutId) {
        return queryRollout("""
                SELECT * FROM rollouts WHERE organization_id = :organizationId AND id = :rolloutId
                """, organizationId, rolloutId);
    }

    public Optional<Rollout> lockByOrganizationAndId(UUID organizationId, UUID rolloutId) {
        return queryRollout("""
                SELECT * FROM rollouts
                WHERE organization_id = :organizationId AND id = :rolloutId
                FOR UPDATE
                """, organizationId, rolloutId);
    }

    public List<Rollout> findAllByOrganization(UUID organizationId) {
        return jdbc.query("""
                SELECT * FROM rollouts
                WHERE organization_id = :organizationId
                ORDER BY created_at DESC
                LIMIT 100
                """, new MapSqlParameterSource("organizationId", organizationId), this::mapRollout);
    }

    public List<RolloutStage> findStages(UUID rolloutId) {
        return jdbc.query("""
                SELECT * FROM rollout_stages
                WHERE rollout_id = :rolloutId
                ORDER BY stage_order
                """, new MapSqlParameterSource("rolloutId", rolloutId), this::mapStage);
    }

    public Optional<RolloutStage> findCurrentStage(UUID rolloutId) {
        return jdbc.query("""
                SELECT * FROM rollout_stages
                WHERE rollout_id = :rolloutId AND status = 'IN_PROGRESS'
                """, new MapSqlParameterSource("rolloutId", rolloutId), this::mapStage)
                .stream().findFirst();
    }

    public Optional<RolloutStage> findNextStage(UUID rolloutId, int stageOrder) {
        return jdbc.query("""
                SELECT * FROM rollout_stages
                WHERE rollout_id = :rolloutId AND stage_order > :stageOrder AND status = 'PENDING'
                ORDER BY stage_order
                LIMIT 1
                """, new MapSqlParameterSource()
                        .addValue("rolloutId", rolloutId)
                        .addValue("stageOrder", stageOrder),
                this::mapStage).stream().findFirst();
    }

    public Optional<RolloutHealthPolicy> findHealthPolicy(UUID rolloutId) {
        return jdbc.query("""
                SELECT metric_name, threshold, minimum_request_count, evaluation_window_seconds, cooldown_seconds
                FROM health_policies
                WHERE rollout_id = :rolloutId
                ORDER BY created_at
                LIMIT 1
                """, new MapSqlParameterSource("rolloutId", rolloutId),
                (rs, rowNum) -> new RolloutHealthPolicy(
                        HealthMetric.valueOf(rs.getString("metric_name")),
                        rs.getDouble("threshold"),
                        rs.getInt("minimum_request_count"),
                        rs.getInt("evaluation_window_seconds"),
                        rs.getInt("cooldown_seconds")))
                .stream().findFirst();
    }

    public Optional<Rollout> findForAssignment(UUID organizationId, UUID featureFlagId, UUID environmentId) {
        return jdbc.query("""
                SELECT * FROM rollouts
                WHERE organization_id = :organizationId
                  AND feature_flag_id = :featureFlagId
                  AND environment_id = :environmentId
                  AND status IN ('IN_PROGRESS', 'PAUSED', 'COMPLETED', 'ROLLED_BACK')
                ORDER BY updated_at DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("featureFlagId", featureFlagId)
                        .addValue("environmentId", environmentId),
                this::mapRollout).stream().findFirst();
    }

    public int update(Rollout rollout) {
        return jdbc.update("""
                UPDATE rollouts
                SET status = :status,
                    current_percentage = :currentPercentage,
                    updated_at = :updatedAt,
                    last_auto_paused_at = :lastAutoPausedAt
                WHERE organization_id = :organizationId AND id = :id
                """, rolloutParameters(rollout));
    }

    public int updateStage(RolloutStage stage) {
        return jdbc.update("""
                UPDATE rollout_stages
                SET status = :status, started_at = :startedAt, completed_at = :completedAt
                WHERE rollout_id = :rolloutId AND id = :id
                """, stageParameters(stage));
    }

    public int skipRemainingStages(UUID rolloutId, Instant completedAt) {
        return jdbc.update("""
                UPDATE rollout_stages
                SET status = 'SKIPPED', completed_at = :completedAt
                WHERE rollout_id = :rolloutId AND status IN ('PENDING', 'IN_PROGRESS')
                """, new MapSqlParameterSource()
                .addValue("rolloutId", rolloutId)
                .addValue("completedAt", Timestamp.from(completedAt)));
    }

    public void insertObservation(UUID observationId, UUID rolloutId, HealthObservation observation, Instant recordedAt) {
        jdbc.update("""
                INSERT INTO health_observations
                    (id, rollout_id, request_count, error_rate, latency_ms, observed_at, recorded_at)
                VALUES (:id, :rolloutId, :requestCount, :errorRate, :latencyMs, :observedAt, :recordedAt)
                """, new MapSqlParameterSource()
                .addValue("id", observationId)
                .addValue("rolloutId", rolloutId)
                .addValue("requestCount", observation.requestCount())
                .addValue("errorRate", observation.errorRate())
                .addValue("latencyMs", observation.latencyMs())
                .addValue("observedAt", Timestamp.from(observation.observedAt()))
                .addValue("recordedAt", Timestamp.from(recordedAt)));
    }

    private void insertStage(RolloutStage stage) {
        jdbc.update("""
                INSERT INTO rollout_stages
                    (id, rollout_id, stage_order, target_percentage, duration_minutes, status, created_at,
                     started_at, completed_at)
                VALUES (:id, :rolloutId, :stageOrder, :targetPercentage, :durationMinutes, :status, :createdAt,
                        :startedAt, :completedAt)
                """, stageParameters(stage));
    }

    private void insertHealthPolicy(UUID id, UUID rolloutId, RolloutHealthPolicy policy, Instant createdAt) {
        jdbc.update("""
                INSERT INTO health_policies
                    (id, rollout_id, metric_name, threshold, evaluation_window_seconds, created_at,
                     minimum_request_count, cooldown_seconds)
                VALUES (:id, :rolloutId, :metricName, :threshold, :evaluationWindowSeconds, :createdAt,
                        :minimumRequestCount, :cooldownSeconds)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("rolloutId", rolloutId)
                .addValue("metricName", policy.metric().name())
                .addValue("threshold", policy.threshold())
                .addValue("evaluationWindowSeconds", policy.evaluationWindowSeconds())
                .addValue("createdAt", Timestamp.from(createdAt))
                .addValue("minimumRequestCount", policy.minimumRequestCount())
                .addValue("cooldownSeconds", policy.cooldownSeconds()));
    }

    private Optional<Rollout> queryRollout(String sql, UUID organizationId, UUID rolloutId) {
        return jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("rolloutId", rolloutId),
                this::mapRollout).stream().findFirst();
    }

    private Rollout mapRollout(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Rollout(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("feature_flag_id", UUID.class),
                rs.getObject("environment_id", UUID.class),
                RolloutStatus.valueOf(rs.getString("status")),
                rs.getInt("current_percentage"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                instantOrNull(rs.getTimestamp("last_auto_paused_at")));
    }

    private RolloutStage mapStage(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RolloutStage(
                rs.getObject("id", UUID.class),
                rs.getObject("rollout_id", UUID.class),
                rs.getInt("stage_order"),
                rs.getInt("target_percentage"),
                rs.getInt("duration_minutes"),
                RolloutStageStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                instantOrNull(rs.getTimestamp("started_at")),
                instantOrNull(rs.getTimestamp("completed_at")));
    }

    private Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private MapSqlParameterSource rolloutParameters(Rollout rollout) {
        return new MapSqlParameterSource()
                .addValue("id", rollout.id())
                .addValue("organizationId", rollout.organizationId())
                .addValue("featureFlagId", rollout.featureFlagId())
                .addValue("environmentId", rollout.environmentId())
                .addValue("status", rollout.status().name())
                .addValue("currentPercentage", rollout.currentPercentage())
                .addValue("createdAt", Timestamp.from(rollout.createdAt()))
                .addValue("updatedAt", Timestamp.from(rollout.updatedAt()))
                .addValue("lastAutoPausedAt", timestampOrNull(rollout.lastAutoPausedAt()));
    }

    private MapSqlParameterSource stageParameters(RolloutStage stage) {
        return new MapSqlParameterSource()
                .addValue("id", stage.id())
                .addValue("rolloutId", stage.rolloutId())
                .addValue("stageOrder", stage.stageOrder())
                .addValue("targetPercentage", stage.targetPercentage())
                .addValue("durationMinutes", stage.durationMinutes())
                .addValue("status", stage.status().name())
                .addValue("createdAt", Timestamp.from(stage.createdAt()))
                .addValue("startedAt", timestampOrNull(stage.startedAt()))
                .addValue("completedAt", timestampOrNull(stage.completedAt()));
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
