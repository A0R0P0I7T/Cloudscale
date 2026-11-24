package com.cloudscale.config.events;

import com.cloudscale.contracts.configuration.ConfigurationEvent;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationEventPublisher {
    private final ConfigurationOutboxRepository outboxRepository;

    public ConfigurationEventPublisher(ConfigurationOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void publish(ConfigurationEvent event) {
        outboxRepository.enqueue(event);
    }
}
