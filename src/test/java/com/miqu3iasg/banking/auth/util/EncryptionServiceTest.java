package com.miqu3iasg.banking.auth.util;

import com.miqu3iasg.banking.auth.service.EncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private static final String VALID_KEY_BASE64 = Base64.getEncoder().encodeToString(new byte[32]); // 32 zero bytes
    private static final String INVALID_KEY_BASE64 = Base64.getEncoder().encodeToString(new byte[16]); // 16 bytes

    @Test
    void encryptDecryptRoundTrip_success() {
        EncryptionService service = new EncryptionService(VALID_KEY_BASE64);
        String plaintext = "Sensitive data 123!";
        String encrypted = service.encrypt(plaintext);
        assertNotNull(encrypted);
        // Decrypt should return the original plaintext
        String decrypted = service.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptProducesDifferentCiphertexts_forSamePlaintext() {
        EncryptionService service = new EncryptionService(VALID_KEY_BASE64);
        String plaintext = "repeatable";
        String enc1 = service.encrypt(plaintext);
        String enc2 = service.encrypt(plaintext);
        // Because a random IV is used, ciphertexts should differ
        assertNotEquals(enc1, enc2);
        // Both decrypt correctly
        assertEquals(plaintext, service.decrypt(enc1));
        assertEquals(plaintext, service.decrypt(enc2));
    }

    @Test
    void constructorRejectsInvalidKeyLength() {
        Executable ctor = () -> new EncryptionService(INVALID_KEY_BASE64);
        assertThrows(IllegalArgumentException.class, ctor);
    }

    @Test
    void constructorRejectsMissingKey() {
        Executable ctor = () -> new EncryptionService("");
        assertThrows(IllegalStateException.class, ctor);
    }
}
