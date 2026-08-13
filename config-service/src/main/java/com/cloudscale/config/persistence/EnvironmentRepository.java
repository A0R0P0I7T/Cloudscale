package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.sql.Timestamp;

@Repository
public class EnvironmentRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final EnvironmentRowMapper rowMapper = new EnvironmentRowMapper();

    public EnvironmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Environment environment) {
        String sql = """
                INSERT INTO environments (id, project_id, environment_key, name, description, created_at, updated_at)
                VALUES (:id, :projectId, :key, :name, :description, :createdAt, :updatedAt)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", environment.id())
                .addValue("projectId", environment.projectId())
                .addValue("key", environment.key())
                .addValue("name", environment.name())
                .addValue("description", environment.description())
                .addValue("createdAt", Timestamp.from(environment.createdAt()))
                .addValue("updatedAt", Timestamp.from(environment.updatedAt())));
    }

    public Optional<Environment> findByProjectAndKey(UUID projectId, String key) {
        String sql = "SELECT * FROM environments WHERE project_id = :projectId AND environment_key = :key";
        return jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("key", key), rowMapper)
                .stream().findFirst();
    }

    public List<Environment> findAllByProject(UUID projectId) {
        String sql = "SELECT * FROM environments WHERE project_id = :projectId ORDER BY environment_key LIMIT 100";
        return jdbc.query(sql, new MapSqlParameterSource("projectId", projectId), rowMapper);
    }
}
