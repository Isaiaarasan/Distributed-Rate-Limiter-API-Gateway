package com.ratelimiter.gateway.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    @Test
    @DisplayName("sha256 should produce consistent 64-character hex hash")
    void testSha256() {
        String input = "rl_mobile-app_demo_key_001";
        String hash1 = HashUtil.sha256(input);
        String hash2 = HashUtil.sha256(input);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2, "Same input must produce identical SHA-256 hash");
    }

    @Test
    @DisplayName("generateApiKey should produce unique keys with client prefix")
    void testGenerateApiKey() {
        String key1 = HashUtil.generateApiKey("mobile-app");
        String key2 = HashUtil.generateApiKey("mobile-app");

        assertTrue(key1.startsWith("rl_mobile-app_"));
        assertTrue(key2.startsWith("rl_mobile-app_"));
        assertNotEquals(key1, key2, "Each generated key must be unique");
    }

    @Test
    @DisplayName("extractPrefix should truncate safely with ellipsis")
    void testExtractPrefix() {
        String key = "rl_mobile-app_1234567890abcdef12345678";
        String prefix = HashUtil.extractPrefix(key);

        assertEquals(27, prefix.length()); // 24 chars + "..."
        assertTrue(prefix.endsWith("..."));
    }
}
