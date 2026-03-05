package com.miqu3iasg.banking_mvp.transaction.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.BankingMvpApplication;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.account.service.AccountService;
import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import com.miqu3iasg.banking.pix.gateway.EfiPixAuthGateway;
import com.miqu3iasg.banking.pix.gateway.EfiEvpGateway;
import com.miqu3iasg.banking.pix.gateway.EfiPixGateway;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
import com.miqu3iasg.banking.pix.service.PixExpirationJob;
import com.miqu3iasg.banking_mvp.efi.pix.gateway.BacenSpec;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@Testcontainers
@SpringBootTest(
	classes = BankingMvpApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("e2e-test")
public abstract class AbstractE2ETestSupport {
	protected static final String PIX_KEY = System.getenv().get("EFI_PIX_KEY");
	protected static final String CPF_1 = "52998224725";
	protected static final String CPF_2 = "111.444.777-35";
	protected static final String CPF_3 = "222.333.444-05";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
		.withDatabaseName("banking_e2e_test")
		.withUsername("test")
		.withPassword("test")
		.withReuse(true);

	@DynamicPropertySource
	static void configureDataSource (DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("outbox.processor.enabled", () -> "false");
	}

	@Autowired
	protected AccountService accountService;
	@Autowired
	protected AccountRepository accountRepository;
	@Autowired
	protected PixKeyRepository keyRepository;
	@Autowired
	protected PixChargeRepository chargeRepository;
	@Autowired
	protected PixExpirationJob expirationJob;
	@Autowired
	protected TransactionTemplate txTemplate;
	@Autowired
	protected CacheManager cacheManager;
	@Autowired
	protected EfiPixProperties props;
	@Autowired
	protected EfiPixGateway efiPixGateway;
	@Autowired
	protected EfiPixAuthGateway authGateway;
	@Autowired
	protected ObjectMapper objectMapper;
	@Autowired
	protected EfiEvpGateway evpGateway;

	private int port;

	@Autowired
	@Qualifier("efiPixWebClient")
	protected WebClient efiPixWebClient;

	protected WebTestClient sandboxClient () {
		String token = authGateway.getAccessToken();

		return WebTestClient
			.bindToWebHandler(exchange -> {
				URI requestUri = exchange.getRequest().getURI();
				String pathAndQuery = requestUri.getRawPath() + (requestUri.getRawQuery() != null ? "?" + requestUri.getRawQuery() : "");

				return efiPixWebClient
					.mutate()
					.defaultHeader("Authorization", "Bearer " + token)
					.defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.build()
					.method(exchange.getRequest().getMethod())
					.uri(pathAndQuery)
					.headers(h -> h.addAll(exchange.getRequest().getHeaders()))
					.body(exchange.getRequest().getBody(), DataBuffer.class)
					.retrieve()
					.toEntity(DataBuffer.class)
					.flatMap(entity -> {
						exchange.getResponse().setStatusCode(entity.getStatusCode());
						exchange.getResponse().getHeaders().addAll(entity.getHeaders());
						return exchange.getResponse().writeWith(Mono.justOrEmpty(entity.getBody()));
					});
			})
			.build();
	}

	@BeforeEach
	void cleanCharges () {
		chargeRepository.deleteAll();
	}

	@BeforeEach
	void evictCache () {
		var cache = cacheManager.getCache("efi-oauth-token");
		if (cache != null) cache.clear();
	}

	protected String generateTxid () {
		return UUID.randomUUID().toString()
			.replace("-", "")
			.substring(0, BacenSpec.TXID_LENGTH)
			.toUpperCase();
	}
}
