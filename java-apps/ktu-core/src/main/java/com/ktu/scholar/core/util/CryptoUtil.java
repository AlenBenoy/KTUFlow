package com.ktu.scholar.core.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256-GCM encryption for credential storage.
 * The key is derived from a device-specific salt via PBKDF2.
 *
 * Layout of encrypted blob (Base64-encoded):
 *   [12-byte IV][16-byte auth tag included in ciphertext by GCM]
 */
public class CryptoUtil {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    KEY_BITS   = 256;
    private static final int    IV_BYTES   = 12;
    private static final int    GCM_TAG    = 128;  // bits
    private static final int    ITERATIONS = 65536;
    private static final String KDF        = "PBKDF2WithHmacSHA256";

    /**
     * Returns a device-specific master password derived from the machine name.
     * Override with a proper secrets manager in production.
     */
    private static char[] masterPassword() {
        String host = System.getProperty("os.name", "unknown") +
                      System.getProperty("user.name", "user");
        return host.toCharArray();
    }

    /** Fixed salt — change this per-application. */
    private static final byte[] SALT = "KTUScholar2024!!".getBytes(StandardCharsets.UTF_8);

    private static SecretKey deriveKey() throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance(KDF);
        KeySpec spec = new PBEKeySpec(masterPassword(), SALT, ITERATIONS, KEY_BITS);
        byte[] keyBytes = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** Encrypts plaintext and returns a Base64-encoded ciphertext blob. */
    public static String encrypt(String plaintext) {
        try {
            SecretKey key = deriveKey();
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] blob = new byte[IV_BYTES + cipherBytes.length];
            System.arraycopy(iv, 0, blob, 0, IV_BYTES);
            System.arraycopy(cipherBytes, 0, blob, IV_BYTES, cipherBytes.length);

            return Base64.getEncoder().encodeToString(blob);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /** Decrypts a Base64-encoded ciphertext blob back to plaintext. */
    public static String decrypt(String encoded) {
        try {
            byte[] blob = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_BYTES];
            byte[] cipherBytes = new byte[blob.length - IV_BYTES];
            System.arraycopy(blob, 0, iv, 0, IV_BYTES);
            System.arraycopy(blob, IV_BYTES, cipherBytes, 0, cipherBytes.length);

            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
