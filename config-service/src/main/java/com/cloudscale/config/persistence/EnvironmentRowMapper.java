package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.Environment;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class EnvironmentRowMapper implements RowMapper<Environment> {
    @Override
    public Environment mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Environment(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getObject("project_id", java.util.UUID.class),
                resultSet.getString("environment_key"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
