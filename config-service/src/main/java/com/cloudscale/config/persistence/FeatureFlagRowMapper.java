package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.FeatureFlag;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class FeatureFlagRowMapper implements RowMapper<FeatureFlag> {
    @Override
    public FeatureFlag mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FeatureFlag(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("environment_id", UUID.class),
                resultSet.getString("feature_key"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getBoolean("enabled"),
                resultSet.getObject("published_version_id", UUID.class),
                resultSet.getLong("revision"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
