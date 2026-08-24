package com.ratelimiter.gateway.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class HashUtil {

    private HashUtil() {}

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String generateApiKey(String clientKey) {
        String random = UUID.randomUUID().toString().replace("-", "");
        return "rl_" + clientKey.toLowerCase().replace(" ", "_") + "_" + random;
    }

    public static String extractPrefix(String apiKey) {
        if (apiKey == null) return "";
        int maxLen = Math.min(apiKey.length(), 24);
        return apiKey.substring(0, maxLen) + "...";
    }
}
