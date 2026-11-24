package com.cloudscale.audit.application;

import com.cloudscale.audit.api.AuditEventResponse;
import com.cloudscale.audit.persistence.AuditEventRepository;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.rollout.RolloutEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter errorCounter;

    public AuditService(AuditEventRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.processedCounter = Counter.builder("cloudscale.audit.events.processed").register(meterRegistry);
        this.duplicateCounter = Counter.builder("cloudscale.audit.events.duplicates").register(meterRegistry);
        this.errorCounter = Counter.builder("cloudscale.audit.events.errors").register(meterRegistry);
    }

    @Transactional
    public boolean record(ConfigurationEvent event) {
        try {
            return countResult(repository.recordConfigurationEvent(event));
        } catch (RuntimeException exception) {
            errorCounter.increment();
            throw exception;
        }
    }

    @Transactional
    public boolean record(RolloutEvent event) {
        try {
            return countResult(repository.recordRolloutEvent(event));
        } catch (RuntimeException exception) {
            errorCounter.increment();
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> list(UUID organizationId, int page, int size) {
        long offset = (long) page * size;
        return repository.findByOrganization(organizationId, size, offset);
    }

    private boolean countResult(boolean inserted) {
        if (inserted) {
            processedCounter.increment();
        } else {
            duplicateCounter.increment();
        }
        return inserted;
    }
}
