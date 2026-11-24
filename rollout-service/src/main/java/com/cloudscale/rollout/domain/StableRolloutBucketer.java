package com.cloudscale.rollout.domain;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class StableRolloutBucketer {
    private static final BigInteger BUCKET_COUNT = BigInteger.valueOf(10_000);

    public int bucket(String stableUserId, UUID featureId, UUID environmentId) {
        if (stableUserId == null || stableUserId.isBlank() || featureId == null || environmentId == null) {
            throw new IllegalArgumentException("A stable user, feature, and environment identity are required");
        }
        String identity = stableUserId + '\u001f' + featureId + '\u001f' + environmentId;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, hash).mod(BUCKET_COUNT).intValue();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
