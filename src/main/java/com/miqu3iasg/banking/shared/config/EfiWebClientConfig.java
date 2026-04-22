package com.miqu3iasg.banking.shared.config;

import com.miqu3iasg.banking.boleto.config.EfiBoletoProperties;
import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.Duration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({EfiPixProperties.class, EfiBoletoProperties.class})
public class EfiWebClientConfig {

	public EfiWebClientConfig () { }

	@Bean
	public WebClient efiPixWebClient (EfiPixProperties props) throws Exception {
		log.info("Configuring Efí Pix WebClient with base URL: {}",
			props.sandbox() ? "https://pix-h.api.efipay.com.br" : "https://pix.api.efipay.com.br");

		return buildMtlsHttpClient(props);
	}

	@Bean
	public WebClient efiBoletoWebClient (EfiBoletoProperties props) throws Exception {
		log.info("Configuring Efí Boleto WebClient whe base URL: {}",
			props.sandbox() ? "https://boleto-h.api.efipay.com.br" : "https://boleto.api.efipay.com.br");

		return buildMtlsHttpClient(props);
	}

	private WebClient buildMtlsHttpClient (EfiProperties props) throws Exception {
		var keyManagerFactory = buildKeyManagerFactory(props);

		var nettySslContext = SslContextBuilder.forClient()
			.keyManager(keyManagerFactory)
			.build();

		var httpClient = HttpClient.create()
			.secure(sslSpec -> sslSpec.sslContext(nettySslContext))
			.responseTimeout(Duration.ofSeconds(props.responseTimeoutInSeconds()));

		return WebClient.builder()
			.baseUrl(props.baseUrl())
			.clientConnector(new ReactorClientHttpConnector(httpClient))
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.build();
	}

	/**
	 * Builds a {@link KeyManagerFactory} initialized with the PKCS#12 certificate configured in
	 * the provided {@link EfiProperties}.
	 *
	 * <p>Efí Bank certificates are issued without a MAC (Message Authentication Code), meaning they
	 * carry no integrity-protection password at the container level. Java 17+ enforces strict
	 * distinction between the two password roles used during PKCS#12 loading:
	 *
	 * <ul>
	 *   <li><b>MAC password</b> — passed to {@link KeyStore#load(InputStream, char[])}. Must be
	 *       {@code null} (not {@code new char[0]}) when the keystore has no MAC; passing any
	 *       non-null value — including an empty array — triggers MAC verification and causes an
	 *       {@code "integrity check failed"} exception.</li>
	 *   <li><b>Key-entry password</b> — passed to {@link KeyManagerFactory#init(KeyStore, char[])}.
	 *       Unlocks the private key entries stored inside the keystore. For Efí certificates this
	 *       is an empty string, so {@code new char[0]} is the correct value when no explicit
	 *       password is configured.</li>
	 * </ul>
	 *
	 * <p>References:
	 * <ul>
	 *   <li><a href="https://bugs.openjdk.org/browse/JDK-8263952">JDK-8263952</a></li>
	 *   <li><a href="https://dev.efipay.com.br/en/docs/api-pix/credenciais">Efí credentials docs</a>
	 *       (key password is {@code ""})</li>
	 * </ul>
	 *
	 * @param props the Efí integration properties containing the certificate path and optional password
	 * @return a fully initialised {@link KeyManagerFactory} backed by the configured certificate
	 * @throws RuntimeException if the certificate file cannot be read or the keystore fails to load
	 * @throws Exception        if {@link KeyManagerFactory} initialisation fails
	 */
	private KeyManagerFactory buildKeyManagerFactory (EfiProperties props) throws Exception {
		var keyStore = KeyStore.getInstance("PKCS12");

		char[] keyEntryPassword = isEffectivelyEmpty(props.certificatePassword())
			? new char[0]
			: props.certificatePassword().toCharArray();

		char[] macPassword = keyEntryPassword.length > 0 ? keyEntryPassword : null;

		try (InputStream inputStream = openCertificate(props.certificatePath())) {
			keyStore.load(inputStream, macPassword);
		} catch (Exception exception) {
			throw new RuntimeException(
				"Failed to load PKCS#12 certificate from path: %s".formatted(props.certificatePath()),
				exception
			);
		}

		var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

		keyManagerFactory.init(keyStore, keyEntryPassword);

		log.debug("Successfully loaded PKCS#12 certificate for Efí WebClient from path: {}",
			props.certificatePath());

		return keyManagerFactory;
	}

	/**
	 * Opens a certificate file from any of the supported path formats:
	 * <ul>
	 *   <li>{@code file:C:\path\to\cert.p12}  — explicit file URI prefix (Windows)</li>
	 *   <li>{@code file:/home/app/cert.p12}    — explicit file URI prefix (Unix)</li>
	 *   <li>{@code C:\path\to\cert.p12}        — bare Windows absolute path</li>
	 *   <li>{@code /home/app/cert.p12}         — bare Unix absolute path</li>
	 *   <li>{@code classpath:certs/cert.p12}   — classpath resource</li>
	 * </ul>
	 * <p>
	 * Using {@code java.nio.file.Path} directly avoids all Spring ResourceLoader
	 * prefix-handling bugs on Windows (backslash vs. forward-slash, ServletContext
	 * resolution, URI encoding issues).
	 */
	private InputStream openCertificate (String rawPath) throws Exception {
		if (rawPath == null || rawPath.isBlank()) {
			throw new IllegalArgumentException("Certificate path must not be blank");
		}

		String path = rawPath.trim();

		if (path.startsWith("classpath:")) {
			String classpathLocation = path.substring("classpath:".length());
			Resource resource = new ClassPathResource(classpathLocation);
			return resource.getInputStream();
		}

		// Strip "file:" prefix if present, then resolve as a plain filesystem path.
		// Paths.get() handles both forward and backslashes on Windows natively.
		if (path.startsWith("file:")) {
			path = path.substring("file:".length());
		}

		Path filePath = Paths.get(path);

		if (!Files.exists(filePath)) {
			throw new IllegalArgumentException(
				"Certificate file not found at: %s (resolved from: %s)"
					.formatted(filePath.toAbsolutePath(), rawPath)
			);
		}

		return Files.newInputStream(filePath);
	}

	private static boolean isEffectivelyEmpty (String value) {
		final String unresolvedSpringPlaceholder = "${";
		return value == null || value.isBlank() || value.startsWith(unresolvedSpringPlaceholder);
	}
}
