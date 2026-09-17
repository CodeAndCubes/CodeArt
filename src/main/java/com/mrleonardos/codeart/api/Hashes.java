package com.mrleonardos.codeart.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Hashes {

    public static final int SHA256_BYTES = 32;
    public static final int SHA256_HEX_LENGTH = SHA256_BYTES * 2;

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private Hashes() {}

    public static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String sha256Hex(byte[] data, int offset, int length) {
        MessageDigest digest = newSha256();
        digest.update(data, offset, length);
        return toHex(digest.digest());
    }

    public static String sha256Hex(byte[] data) {
        return sha256Hex(data, 0, data.length);
    }

    public static String toHex(byte[] raw) {
        char[] out = new char[raw.length * 2];
        for (int i = 0; i < raw.length; i++) {
            int v = raw[i] & 0xFF;
            out[i * 2] = HEX_DIGITS[v >>> 4];
            out[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        int length = hex.length();
        if ((length & 1) != 0) {
            throw new IllegalArgumentException("Hex string of odd length: " + length);
        }
        byte[] out = new byte[length / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = digit(hex.charAt(i * 2));
            int lo = digit(hex.charAt(i * 2 + 1));
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static boolean isSha256Hex(String value) {
        if (value == null || value.length() != SHA256_HEX_LENGTH) {
            return false;
        }
        for (int i = 0; i < SHA256_HEX_LENGTH; i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private static int digit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new IllegalArgumentException("Not a hex digit: " + c);
    }
}
