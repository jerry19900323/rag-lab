package com.example.raglab.infrastructure.auth;

import com.example.raglab.application.ModelConfigurationUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-GCM encryption for stored model API keys. */
@Component
public class ModelSecretCipher {

    private static final int NONCE_LENGTH = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ModelSecretCipher(@Value("${rag.model-config.encryption-key:}") String encodedKey) {
        byte[] bytes;
        try {
            bytes = encodedKey.isBlank() ? null : Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            bytes = null;
        }
        key = bytes != null && bytes.length == 32 ? new SecretKeySpec(bytes, "AES") : null;
    }

    public String encrypt(String plainText) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new ModelConfigurationUnavailableException("Model credential encryption is unavailable");
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length <= NONCE_LENGTH) {
                throw new GeneralSecurityException("Invalid encrypted credential");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, data, 0, NONCE_LENGTH));
            return new String(cipher.doFinal(data, NONCE_LENGTH, data.length - NONCE_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new ModelConfigurationUnavailableException("Stored model credential could not be decrypted");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new ModelConfigurationUnavailableException(
                    "MODEL_CONFIG_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
        }
    }
}
