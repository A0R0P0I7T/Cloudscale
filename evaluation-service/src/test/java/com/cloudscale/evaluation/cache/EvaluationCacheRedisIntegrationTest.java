package com.cloudscale.evaluation.cache;

import com.cloudscale.evaluation.client.FeatureEvaluationConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class EvaluationCacheRedisIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Test
    void staleFetchesAndDuplicateInvalidationsNeverRegressFreshSnapshot() {
        RedisConnectionFactory connectionFactory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        ((LettuceConnectionFactory) connectionFactory).afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            EvaluationCache cache = new EvaluationCache(redis, objectMapper, new EvaluationCacheProperties(60));

            String invalidatedKey = "test:evaluation:" + UUID.randomUUID();
            assertThat(cache.invalidate(invalidatedKey, 12)).isTrue();
            assertThat(cache.put(invalidatedKey, configuration(11))).isFalse();
            assertThat(cache.get(invalidatedKey).status()).isEqualTo(CacheLookup.Status.MISS);

            String warmKey = "test:evaluation:" + UUID.randomUUID();
            assertThat(cache.invalidate(warmKey, 12)).isTrue();
            assertThat(cache.put(warmKey, configuration(12))).isTrue();
            assertThat(cache.invalidate(warmKey, 12)).isFalse();
            assertThat(cache.invalidate(warmKey, 11)).isFalse();
            assertThat(cache.get(warmKey).status()).isEqualTo(CacheLookup.Status.HIT);
        } finally {
            ((LettuceConnectionFactory) connectionFactory).destroy();
        }
    }

    private FeatureEvaluationConfiguration configuration(long revision) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new FeatureEvaluationConfiguration(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "checkout",
                true,
                true,
                UUID.randomUUID(),
                1,
                objectMapper.valueToTree(Map.of("variant", "control")),
                List.of(),
                revision,
                Instant.now());
    }
}
