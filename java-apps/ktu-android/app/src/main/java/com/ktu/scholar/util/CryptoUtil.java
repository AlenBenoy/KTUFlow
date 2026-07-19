package com.ktu.scholar.util;

import android.content.Context;

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
 * AES-256-GCM encryption for Android credential storage.
 * Key is derived from a device-specific value via PBKDF2.
 *
 * Note: For production, derive the key from Android Keystore instead of
 * a static password — this gives hardware-backed key protection.
 */
public class CryptoUtil {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    KEY_BITS   = 256;
    private static final int    IV_BYTES   = 12;
    private static final int    GCM_TAG    = 128;
    private static final int    ITERATIONS = 65536;
    private static final byte[] SALT       = "KTUScholar2024!!".getBytes(StandardCharsets.UTF_8);

    public static String encrypt(String plaintext, Context ctx) {
        try {
            SecretKey key = deriveKey(ctx);
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG, iv));
            byte[] cipher_bytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] blob = new byte[IV_BYTES + cipher_bytes.length];
            System.arraycopy(iv, 0, blob, 0, IV_BYTES);
            System.arraycopy(cipher_bytes, 0, blob, IV_BYTES, cipher_bytes.length);
            return Base64.getEncoder().encodeToString(blob);
        } catch (Exception e) { throw new RuntimeException("Encryption failed", e); }
    }

    public static String decrypt(String encoded, Context ctx) {
        try {
            byte[] blob = Base64.getDecoder().decode(encoded);
            byte[] iv   = new byte[IV_BYTES];
            byte[] data = new byte[blob.length - IV_BYTES];
            System.arraycopy(blob, 0, iv, 0, IV_BYTES);
            System.arraycopy(blob, IV_BYTES, data, 0, data.length);
            SecretKey key = deriveKey(ctx);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG, iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RuntimeException("Decryption failed", e); }
    }

    private static SecretKey deriveKey(Context ctx) throws Exception {
        // Use Android ID as device-specific master password
        String androidId = android.provider.Settings.Secure.getString(
                ctx.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        char[] masterPass = (androidId != null ? androidId : "ktu_default").toCharArray();
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(masterPass, SALT, ITERATIONS, KEY_BITS);
        byte[] keyBytes = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
