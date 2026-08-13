package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.Project;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProjectRowMapper implements RowMapper<Project> {
    @Override
    public Project mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Project(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getObject("organization_id", java.util.UUID.class),
                resultSet.getString("project_key"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
