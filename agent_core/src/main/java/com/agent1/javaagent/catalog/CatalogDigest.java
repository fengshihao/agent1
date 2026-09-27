package com.agent1.javaagent.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class CatalogDigest {

    private CatalogDigest() {
    }

    public static String sha256Prefix(byte[] data) {
        return "sha256:" + hexSha256(data);
    }

    public static boolean matches(String expectedDigest, byte[] data) {
        if (expectedDigest == null || expectedDigest.isBlank()) {
            return false;
        }
        String normalized = expectedDigest.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length());
        }
        return hexSha256(data).equals(normalized);
    }

    private static String hexSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
