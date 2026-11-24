package com.cloudscale.evaluation.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationCacheFailureTest {
    @Test
    void propagatesRedisInvalidationFailureForKafkaRetry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<List<String>>any(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        EvaluationCache cache = new EvaluationCache(redis, new ObjectMapper(), new EvaluationCacheProperties(60));

        assertThatThrownBy(() -> cache.invalidate("cloudscale:evaluation-config:key", 10))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
