package com.cloudscale.rollout.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StableRolloutBucketerTest {
    private final StableRolloutBucketer bucketer = new StableRolloutBucketer();
    private final UUID featureId = UUID.fromString("b2bb1375-a6d7-458d-a732-4ccab7a9df04");
    private final UUID environmentId = UUID.fromString("065d8c23-5b9b-40f1-bfce-ef541f2387d2");

    @Test
    void assignsSameIdentityToSameBucketAcrossRepeatedCalls() {
        int first = bucketer.bucket("customer-123", featureId, environmentId);

        assertThat(first).isBetween(0, 9_999);
        assertThat(bucketer.bucket("customer-123", featureId, environmentId)).isEqualTo(first);
    }

    @Test
    void spreadsTenThousandStableIdentitiesNearFiftyPercent() {
        long assigned = IntStream.range(0, 10_000)
                .mapToLong(index -> bucketer.bucket("customer-" + index, featureId, environmentId))
                .filter(bucket -> bucket < 5_000)
                .count();

        assertThat(assigned).isBetween(4_750L, 5_250L);
    }
}
