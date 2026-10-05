package fr.expand.project.importdata.access;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Version 0 is the original salted SHA-256; version 1 is PBKDF2-HMAC-SHA256. */
final class PasswordHasher {
    static final int CURRENT_VERSION = 1;
    static final int ITERATIONS = 600_000;
    static final int MAX_PASSWORD_LENGTH = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    record Hash(String hash, String salt, int version, int iterations) {}

    static Hash hash(String password) {
        if (password == null || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must contain at most 4096 characters");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new Hash(
                Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS)),
                Base64.getEncoder().encodeToString(salt),
                CURRENT_VERSION,
                ITERATIONS);
    }

    static boolean verify(
            String password, String saltText, String hashText, int version, int iterations) {
        if (password == null || password.length() > MAX_PASSWORD_LENGTH) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(saltText);
            byte[] expected = Base64.getDecoder().decode(hashText);
            if (salt.length < 16 || salt.length > 64 || expected.length != 32) {
                return false;
            }
            byte[] actual;
            if (version == 0) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(salt);
                actual = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            } else if (version == CURRENT_VERSION && iterations > 0 && iterations <= 2_000_000) {
                actual = derive(password, salt, iterations);
            } else {
                return false;
            }
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | GeneralSecurityException invalid) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", unavailable);
        } finally {
            spec.clearPassword();
        }
    }
}
