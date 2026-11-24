package com.cloudscale.config.events;

import com.cloudscale.config.events.ConfigurationOutboxRepository.PendingEvent;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "cloudscale.outbox.dispatcher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConfigurationOutboxDispatcher {
    static final String TOPIC = "cloudscale.configuration-events";
    private static final int BATCH_SIZE = 25;
    private static final Logger log = LoggerFactory.getLogger(ConfigurationOutboxDispatcher.class);

    private final ConfigurationOutboxRepository repository;
    private final KafkaTemplate<String, ConfigurationEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter publishedCounter;
    private final Counter publishErrorCounter;

    public ConfigurationOutboxDispatcher(
            ConfigurationOutboxRepository repository,
            KafkaTemplate<String, ConfigurationEvent> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.publishedCounter = Counter.builder("cloudscale.configuration.events.published")
                .register(meterRegistry);
        this.publishErrorCounter = Counter.builder("cloudscale.configuration.events.publish.errors")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${cloudscale.outbox.dispatcher.fixed-delay-ms:1000}")
    @Transactional
    public void dispatchPending() {
        List<PendingEvent> pendingEvents = repository.lockNextBatch(BATCH_SIZE, Instant.now());
        for (PendingEvent pending : pendingEvents) {
            try {
                ConfigurationEvent event = objectMapper.readValue(pending.payload(), ConfigurationEvent.class);
                kafkaTemplate.send(TOPIC, pending.messageKey(), event).get(10, TimeUnit.SECONDS);
                repository.markPublished(pending.eventId(), Instant.now());
                publishedCounter.increment();
            } catch (Exception exception) {
                Throwable cause = rootCause(exception);
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                int attempts = pending.attempts() + 1;
                long delaySeconds = Math.min(3600L, 1L << Math.min(attempts, 12));
                repository.scheduleRetry(pending.eventId(), attempts,
                        Instant.now().plusSeconds(delaySeconds), cause.getClass().getSimpleName());
                publishErrorCounter.increment();
                log.warn("Configuration event delivery will retry eventId={} attempts={} errorType={}",
                        pending.eventId(), attempts, cause.getClass().getSimpleName());
                if (exception instanceof InterruptedException) {
                    return;
                }
            }
        }
    }

    private Throwable rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
