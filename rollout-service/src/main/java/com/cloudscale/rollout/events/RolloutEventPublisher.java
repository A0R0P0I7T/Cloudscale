package com.cloudscale.rollout.events;

import com.cloudscale.contracts.rollout.RolloutEvent;
import org.springframework.stereotype.Component;

@Component
public class RolloutEventPublisher {
    private final RolloutOutboxRepository outboxRepository;

    public RolloutEventPublisher(RolloutOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void publish(RolloutEvent event) {
        outboxRepository.enqueue(event);
    }
}
