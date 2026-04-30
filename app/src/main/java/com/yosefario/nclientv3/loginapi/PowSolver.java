package com.yosefario.nclientv3.loginapi;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// digest = SHA-256(UTF-8(challenge + decimal_nonce)); accept when first `difficulty` bits are zero.
// Nonce goes back to the server as its decimal string. Verified against /api/v2/auth/login 2026-04-30.
public final class PowSolver {

    private PowSolver() {}

    @NonNull
    public static String solve(@NonNull String challenge, int difficulty) {
        if (difficulty < 0 || difficulty > 256) {
            throw new IllegalArgumentException("difficulty out of range: " + difficulty);
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] challengeBytes = challenge.getBytes(StandardCharsets.UTF_8);
        long nonce = 0;
        while (true) {
            String nonceStr = Long.toString(nonce);
            digest.reset();
            digest.update(challengeBytes);
            digest.update(nonceStr.getBytes(StandardCharsets.US_ASCII));
            if (hasLeadingZeroBits(digest.digest(), difficulty)) {
                return nonceStr;
            }
            nonce++;
        }
    }

    static boolean hasLeadingZeroBits(byte[] hash, int bits) {
        int fullBytes = bits >>> 3;
        for (int i = 0; i < fullBytes; i++) {
            if (hash[i] != 0) return false;
        }
        int remaining = bits & 7;
        if (remaining == 0) return true;
        int next = hash[fullBytes] & 0xFF;
        return (next >>> (8 - remaining)) == 0;
    }
}
