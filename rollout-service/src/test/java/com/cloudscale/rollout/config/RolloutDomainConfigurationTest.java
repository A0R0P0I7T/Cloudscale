package com.cloudscale.rollout.config;

import com.cloudscale.rollout.domain.RolloutRules;
import com.cloudscale.rollout.domain.StableRolloutBucketer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RolloutDomainConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RolloutDomainConfiguration.class);

    @Test
    void registersTheStatelessDomainCollaboratorsRequiredByTheApplicationService() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RolloutRules.class);
            assertThat(context).hasSingleBean(StableRolloutBucketer.class);

            RolloutRules rules = context.getBean(RolloutRules.class);
            assertThat(rules.transition(
                    com.cloudscale.rollout.domain.RolloutStatus.DRAFT,
                    com.cloudscale.rollout.domain.RolloutAction.START))
                    .isEqualTo(com.cloudscale.rollout.domain.RolloutStatus.IN_PROGRESS);
            assertThat(context.getBean(StableRolloutBucketer.class)
                    .bucket("local-benchmark", java.util.UUID.fromString("b2bb1375-a6d7-458d-a732-4ccab7a9df04"),
                            java.util.UUID.fromString("065d8c23-5b9b-40f1-bfce-ef541f2387d2")))
                    .isBetween(0, 9_999);
        });
    }
}
