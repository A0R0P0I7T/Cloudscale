package com.cloudscale.evaluation.events;

import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.evaluation.cache.EvaluationCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationEventHandler.class);

    private final EvaluationCache evaluationCache;
    private final Counter processedCounter;
    private final Counter staleCounter;
    private final Counter errorCounter;

    public ConfigurationEventHandler(EvaluationCache evaluationCache, MeterRegistry meterRegistry) {
        this.evaluationCache = evaluationCache;
        this.processedCounter = Counter.builder("cloudscale.evaluation.configuration.events.processed")
                .tag("eventType", "all")
                .register(meterRegistry);
        this.staleCounter = Counter.builder("cloudscale.evaluation.configuration.events.stale")
                .tag("eventType", "all")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("cloudscale.evaluation.configuration.events.errors")
                .tag("eventType", "all")
                .register(meterRegistry);
    }

    public void handle(ConfigurationEvent event) {
        try {
            if (event.revision() == null) {
                log.warn("Ignored configuration event without revision eventId={}", event.eventId());
                return;
            }

            String cacheKey = "cloudscale:evaluation-config:" + event.organizationId() + ":"
                    + event.projectKey() + ":" + event.environmentKey() + ":" + event.featureKey();
            
            boolean invalidated = evaluationCache.invalidate(cacheKey, event.revision());
            
            if (invalidated) {
                processedCounter.increment();
                log.info("Processed configuration event eventId={} eventType={} cacheKey={} revision={}",
                        event.eventId(), event.eventType(), cacheKey, event.revision());
            } else {
                staleCounter.increment();
                log.info("Ignored stale configuration event eventId={} eventType={} cacheKey={} revision={}",
                        event.eventId(), event.eventType(), cacheKey, event.revision());
            }
        } catch (Exception exception) {
            errorCounter.increment();
            log.error("Failed to handle configuration event eventId={} eventType={}",
                    event.eventId(), event.eventType(), exception);
            throw exception;
        }
    }
}
