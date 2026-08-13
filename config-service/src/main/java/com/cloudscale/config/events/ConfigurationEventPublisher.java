package com.cloudscale.config.events;

import com.cloudscale.contracts.configuration.ConfigurationEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationEventPublisher.class);
    static final String TOPIC = "cloudscale.configuration-events";

    private final KafkaTemplate<String, ConfigurationEvent> kafkaTemplate;
    private final Counter publishedCounter;
    private final Counter publishErrorCounter;

    public ConfigurationEventPublisher(
            KafkaTemplate<String, ConfigurationEvent> kafkaTemplate,
            MeterRegistry meterRegistry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishedCounter = Counter.builder("cloudscale.configuration.events.published")
                .tag("eventType", "all")
                .register(meterRegistry);
        this.publishErrorCounter = Counter.builder("cloudscale.configuration.events.publish.errors")
                .tag("eventType", "all")
                .register(meterRegistry);
    }

    public void publish(ConfigurationEvent event) {
        String messageKey = event.organizationId() + ":" + event.projectKey() + ":"
                + event.environmentKey() + ":" + event.featureKey();
        try {
            kafkaTemplate.send(TOPIC, messageKey, event)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            publishErrorCounter.increment();
                            log.error("Failed to publish configuration event eventId={} eventType={}",
                                    event.eventId(), event.eventType(), exception);
                        } else {
                            publishedCounter.increment();
                            log.info("Published configuration event eventId={} eventType={} topic={} partition={}",
                                    event.eventId(), event.eventType(), TOPIC,
                                    result.getRecordMetadata().partition());
                        }
                    });
        } catch (RuntimeException exception) {
            publishErrorCounter.increment();
            log.error("Failed to send configuration event eventId={} eventType={}",
                    event.eventId(), event.eventType(), exception);
        }
    }
}
