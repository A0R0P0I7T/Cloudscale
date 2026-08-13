package com.cloudscale.config.persistence;

import com.cloudscale.config.domain.TargetingRule;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TargetingRuleRowMapper implements RowMapper<TargetingRule> {
    private final JsonDocumentMapper jsonDocumentMapper;

    public TargetingRuleRowMapper(JsonDocumentMapper jsonDocumentMapper) {
        this.jsonDocumentMapper = jsonDocumentMapper;
    }

    @Override
    public TargetingRule mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TargetingRule(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("feature_version_id", UUID.class),
                resultSet.getInt("priority"),
                jsonDocumentMapper.read(resultSet.getString("conditions")),
                jsonDocumentMapper.read(resultSet.getString("value")),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
