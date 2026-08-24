package com.ratelimiter.gateway.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Utility for hashing API keys and generating new ones.
 *
 * Security note:
 *   - API keys are NEVER stored in plaintext in MySQL.
 *   - Only a SHA-256 hash is stored. The raw key is shown to the client ONCE at creation.
 *   - On each request, the incoming key is hashed and compared against the stored hash.
 */
public final class HashUtil {

    private HashUtil() {
        // Utility class - no instances
    }

    /**
     * Computes a SHA-256 hex digest of the input string.
     * Used to hash API keys before storing in MySQL.
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates a new unique API key.
     * Format: rl_{clientKey}_{randomUUID}
     * Example: rl_mobile-app_a1b2c3d4e5f6...
     */
    public static String generateApiKey(String clientKey) {
        String random = UUID.randomUUID().toString().replace("-", "");
        return "rl_" + clientKey.toLowerCase().replace(" ", "_") + "_" + random;
    }

    /**
     * Extracts a short prefix from the full API key for display purposes.
     * Allows admin to identify which key belongs to which client without exposing the full key.
     * Example: "rl_mobile-app_a1b2c3..." → "rl_mobile-app_a1b2"
     */
    public static String extractPrefix(String apiKey) {
        if (apiKey == null) return "";
        int maxLen = Math.min(apiKey.length(), 24);
        return apiKey.substring(0, maxLen) + "...";
    }
}
