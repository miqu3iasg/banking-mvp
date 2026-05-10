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

    private final WebClient.Builder webClientBuilder;

    public EfiWebClientConfig(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

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

        return webClientBuilder.clone()
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

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
