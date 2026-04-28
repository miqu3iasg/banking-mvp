package com.miqu3iasg.banking.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class EncryptionService {

	private static final String ALGORITHM = "AES/GCM/NoPadding";
	private static final int GCM_IV_LENGTH = 12;
	private static final int GCM_TAG_LENGTH = 128;

	private final SecretKey encryptionKey;

	public EncryptionService (@Value("${auth.encryption.key:}") String base64Key) {
		if (base64Key == null || base64Key.isBlank()) {
			String envKey = System.getenv("AUTH_ENCRYPTION_KEY");
			if (envKey != null && !envKey.isBlank()) {
				base64Key = envKey;
			}
		}

		if (base64Key == null || base64Key.isBlank()) {
			throw new IllegalStateException("Encryption key must be configured via auth.encryption.key property or AUTH_ENCRYPTION_KEY environment variable. "
				+ "In production, source this from Vault, AWS KMS, or an equivalent secrets manager.");
		}

		byte[] keyBytes = Base64.getDecoder().decode(base64Key);
		if (keyBytes.length != 32) {
			throw new IllegalArgumentException("Encryption key must be 32 bytes (256 bits) for AES-256-GCM, got " + keyBytes.length + " bytes");
		}
		this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
		log.info("Encryption service initialized with AES-256-GCM");
	}

	public String encrypt (String plaintext) {
		try {
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			byte[] iv = new byte[GCM_IV_LENGTH];
			new SecureRandom().nextBytes(iv);
			cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
			buffer.put((byte) iv.length);
			buffer.put(iv);
			buffer.put(ciphertext);

			return Base64.getEncoder().encodeToString(buffer.array());
		} catch (Exception e) {
			throw new RuntimeException("Failed to encrypt value", e);
		}
	}

	public String decrypt (String encrypted) {
		try {
			byte[] data = Base64.getDecoder().decode(encrypted);
			ByteBuffer buffer = ByteBuffer.wrap(data);

			int ivLength = buffer.get();
			byte[] iv = new byte[ivLength];
			buffer.get(iv);
			byte[] ciphertext = new byte[buffer.remaining()];
			buffer.get(ciphertext);

			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

			byte[] plaintext = cipher.doFinal(ciphertext);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException("Failed to decrypt value", e);
		}
	}
}
