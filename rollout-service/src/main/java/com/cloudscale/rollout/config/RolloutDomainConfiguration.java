package com.cloudscale.rollout.config;

import com.cloudscale.rollout.domain.RolloutRules;
import com.cloudscale.rollout.domain.StableRolloutBucketer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RolloutDomainConfiguration {
    @Bean
    RolloutRules rolloutRules() {
        return new RolloutRules();
    }

    @Bean
    StableRolloutBucketer stableRolloutBucketer() {
        return new StableRolloutBucketer();
    }
}
