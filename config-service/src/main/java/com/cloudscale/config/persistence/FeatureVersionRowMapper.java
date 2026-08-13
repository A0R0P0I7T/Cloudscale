package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.FeatureVersion;
import com.cloudscale.config.domain.VersionStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class FeatureVersionRowMapper implements RowMapper<FeatureVersion> {
    private final JsonDocumentMapper jsonDocumentMapper;

    public FeatureVersionRowMapper(JsonDocumentMapper jsonDocumentMapper) {
        this.jsonDocumentMapper = jsonDocumentMapper;
    }

    @Override
    public FeatureVersion mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FeatureVersion(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("feature_flag_id", UUID.class),
                resultSet.getInt("version_number"),
                VersionStatus.valueOf(resultSet.getString("status")),
                jsonDocumentMapper.read(resultSet.getString("value")),
                resultSet.getObject("created_by", UUID.class),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
