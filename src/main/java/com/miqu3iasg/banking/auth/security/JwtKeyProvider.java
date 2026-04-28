package com.miqu3iasg.banking.auth.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class JwtKeyProvider {

	private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

	private static final String KEY_ALGORITHM = "RSA";
	private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

	@Value("${auth.jwt.public-key-path:}")
	private String publicKeyPath;

	@Value("${auth.jwt.private-key-path:}")
	private String privateKeyPath;

	@Value("${auth.jwt.key-size:4096}")
	private int keySize;

	@Getter
	private PrivateKey privateKey;
	@Getter
	private PublicKey publicKey;
	@Getter
	private KeyPair currentKeyPair;
	@Getter
	private KeyPair previousKeyPair;
	private long keyRotationTimestamp;

	@PostConstruct
	public void init () {
		try {
			if (!publicKeyPath.isEmpty() && !privateKeyPath.isEmpty()) {
				loadKeysFromFiles();
			} else {
				if (isProductionProfile()) {
					throw new IllegalStateException("JWT key paths must be configured in production. Use Vault or KMS.");
				}
				generateKeys();
			}
			log.info("JWT key provider initialized successfully");
		} catch (Exception e) {
			log.error("Failed to initialize JWT key provider", e);
			throw new IllegalStateException("Failed to initialize JWT key provider", e);
		}
	}

	private boolean isProductionProfile () {
		String activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
		return activeProfiles != null && (activeProfiles.contains("prod") || activeProfiles.contains("production"));
	}

	private void loadKeysFromFiles () throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		loadKeysFromFiles(publicKeyPath, privateKeyPath);
	}

	private void loadKeysFromFiles (String pubPath, String privPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] publicKeyBytes = Files.readAllBytes(Path.of(pubPath));
		byte[] privateKeyBytes = Files.readAllBytes(Path.of(privPath));

		X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
		PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);

		KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
		this.publicKey = keyFactory.generatePublic(publicKeySpec);
		this.privateKey = keyFactory.generatePrivate(privateKeySpec);
		this.currentKeyPair = new KeyPair(publicKey, privateKey);
	}

	private void generateKeys () throws NoSuchAlgorithmException, IOException {
		Path keyDir = Path.of("data/keys");
		Path pubKeyPath = keyDir.resolve("jwt-public.der");
		Path privKeyPath = keyDir.resolve("jwt-private.der");

		if (Files.exists(pubKeyPath) && Files.exists(privKeyPath)) {
			try {
				loadKeysFromFiles(pubKeyPath.toString(), privKeyPath.toString());
				log.info("Loaded persisted RSA key pair from data/keys");
				return;
			} catch (InvalidKeySpecException e) {
				log.warn("Persisted key files are corrupted, generating new keys: {}", e.getMessage());
			}
		}

		KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
		keyGen.initialize(keySize, new SecureRandom());
		this.currentKeyPair = keyGen.generateKeyPair();
		this.privateKey = currentKeyPair.getPrivate();
		this.publicKey = currentKeyPair.getPublic();

		Files.createDirectories(keyDir);
		// Restrict permissions: owner read/write only for private key, owner read for public key
		Files.write(pubKeyPath, publicKey.getEncoded());
		pubKeyPath.toFile().setReadable(true, true);
		Files.write(privKeyPath, privateKey.getEncoded());
		privKeyPath.toFile().setReadable(true, true);
		privKeyPath.toFile().setWritable(true, true);
		privKeyPath.toFile().setExecutable(false, true);

		log.warn("Generated new RSA key pair and persisted to data/keys. "
			+ "For production, use Vault or KMS via auth.jwt.public-key-path / auth.jwt.private-key-path.");
	}

	public void rotateKeys () {
		previousKeyPair = currentKeyPair;
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
			keyGen.initialize(keySize, new SecureRandom());
			currentKeyPair = keyGen.generateKeyPair();
			privateKey = currentKeyPair.getPrivate();
			publicKey = currentKeyPair.getPublic();
			keyRotationTimestamp = System.currentTimeMillis();
			log.info("JWT keys rotated. Previous key will remain valid for overlap window.");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to rotate keys", e);
		}
	}

	public boolean isKeyFromRotation (long tokenIssuedAt) {
		if (previousKeyPair == null || keyRotationTimestamp == 0) {
			return false;
		}
		return tokenIssuedAt < keyRotationTimestamp;
	}

	public PublicKey getKeyForToken (long issuedAt) {
		if (isKeyFromRotation(issuedAt) && previousKeyPair != null) {
			return previousKeyPair.getPublic();
		}
		return currentKeyPair.getPublic();
	}

	public String getKeyId () {
		try {
			return Base64.getUrlEncoder().encodeToString(
				MessageDigest.getInstance("SHA-256")
					.digest(publicKey.getEncoded())
			).substring(0, 16);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 not available", e);
		}
	}
}
