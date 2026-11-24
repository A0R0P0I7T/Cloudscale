package com.cloudscale.rollout.persistence;

import com.cloudscale.rollout.domain.Rollout;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RolloutRepositoryTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void assignmentLookupIncludesRolledBackRowsToPreventFallbackToOlderExposure() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        List<Rollout> emptyResult = List.of();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(emptyResult);
        RolloutRepository repository = new RolloutRepository(jdbc);

        repository.findForAssignment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("'ROLLED_BACK'", "ORDER BY updated_at DESC", "LIMIT 1");
    }
}
