package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.Project;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProjectRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ProjectRowMapper rowMapper = new ProjectRowMapper();

    public ProjectRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Project project) {
        String sql = """
                INSERT INTO projects (id, organization_id, project_key, name, description, created_at, updated_at)
                VALUES (:id, :organizationId, :key, :name, :description, :createdAt, :updatedAt)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", project.id())
                .addValue("organizationId", project.organizationId())
                .addValue("key", project.key())
                .addValue("name", project.name())
                .addValue("description", project.description())
                .addValue("createdAt", Timestamp.from(project.createdAt()))
                .addValue("updatedAt", Timestamp.from(project.updatedAt())));
    }

    public Optional<Project> findByOrganizationAndKey(UUID organizationId, String key) {
        String sql = "SELECT * FROM projects WHERE organization_id = :organizationId AND project_key = :key";
        return jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("key", key), rowMapper)
                .stream().findFirst();
    }

    public List<Project> findAllByOrganization(UUID organizationId) {
        String sql = "SELECT * FROM projects WHERE organization_id = :organizationId ORDER BY project_key LIMIT 100";
        return jdbc.query(sql, new MapSqlParameterSource("organizationId", organizationId), rowMapper);
    }

    public Optional<Project> findById(UUID id) {
        String sql = "SELECT * FROM projects WHERE id = :id";
        return jdbc.query(sql, new MapSqlParameterSource("id", id), rowMapper).stream().findFirst();
    }
}
