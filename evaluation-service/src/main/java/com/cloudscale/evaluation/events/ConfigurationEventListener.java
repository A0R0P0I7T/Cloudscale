package com.cloudscale.evaluation.events;

import com.cloudscale.contracts.configuration.ConfigurationEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationEventListener {

    private final ConfigurationEventHandler eventHandler;

    public ConfigurationEventListener(ConfigurationEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @KafkaListener(topics = "cloudscale.configuration-events", groupId = "cloudscale-evaluation-service")
    public void onEvent(ConfigurationEvent event) {
        eventHandler.handle(event);
    }
}
