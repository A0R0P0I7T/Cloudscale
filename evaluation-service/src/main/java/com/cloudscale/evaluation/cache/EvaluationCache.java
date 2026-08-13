package com.cloudscale.evaluation.cache;

import com.cloudscale.evaluation.client.FeatureEvaluationConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class EvaluationCache {
    private static final Logger log = LoggerFactory.getLogger(EvaluationCache.class);

    private static final String PUT_SCRIPT = """
            local dataKey = KEYS[1]
            local revKey = KEYS[2]
            local data = ARGV[1]
            local rev = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            
            local currentRev = tonumber(redis.call('GET', revKey) or '-1')
            if rev >= currentRev then
                redis.call('SET', dataKey, data, 'EX', ttl)
                redis.call('SET', revKey, rev, 'EX', ttl)
                return 1
            else
                return 0
            end
            """;

    private static final String INVALIDATE_SCRIPT = """
            local dataKey = KEYS[1]
            local revKey = KEYS[2]
            local eventRev = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            
            local currentRev = tonumber(redis.call('GET', revKey) or '-1')
            if eventRev >= currentRev then
                redis.call('DEL', dataKey)
                redis.call('SET', revKey, eventRev, 'EX', ttl)
                return 1
            else
                return 0
            end
            """;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public EvaluationCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            EvaluationCacheProperties properties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(properties.ttlSeconds());
    }

    public CacheLookup get(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return CacheLookup.miss();
            }
            return CacheLookup.hit(objectMapper.readValue(value, FeatureEvaluationConfiguration.class));
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("Evaluation cache read failed for key {}", key, exception);
            return CacheLookup.error();
        }
    }

    public boolean put(String key, FeatureEvaluationConfiguration configuration) {
        try {
            String revKey = key + ":revision";
            String data = objectMapper.writeValueAsString(configuration);
            Long result = redis.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(PUT_SCRIPT, Long.class),
                    java.util.List.of(key, revKey),
                    data,
                    String.valueOf(configuration.revision()),
                    String.valueOf(ttl.getSeconds())
            );
            return result != null && result == 1L;
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("Evaluation cache write failed for key {}", key, exception);
            return false;
        }
    }

    public boolean invalidate(String key, long revision) {
        try {
            String revKey = key + ":revision";
            Long result = redis.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(INVALIDATE_SCRIPT, Long.class),
                    java.util.List.of(key, revKey),
                    String.valueOf(revision),
                    String.valueOf(ttl.getSeconds())
            );
            return result != null && result == 1L;
        } catch (DataAccessException exception) {
            log.warn("Evaluation cache invalidation failed for key {}", key, exception);
            return false;
        }
    }
}
