package com.cloudscale.audit.events;

import com.cloudscale.audit.application.AuditService;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.rollout.RolloutEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {
    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @KafkaListener(topics = "cloudscale.configuration-events")
    public void onConfigurationEvent(ConfigurationEvent event) {
        auditService.record(event);
    }

    @KafkaListener(topics = "cloudscale.rollout-events")
    public void onRolloutEvent(RolloutEvent event) {
        auditService.record(event);
    }
}
