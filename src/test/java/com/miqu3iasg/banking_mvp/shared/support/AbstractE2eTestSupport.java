package com.miqu3iasg.banking_mvp.shared.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.BankingMvpApplication;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.account.service.AccountService;
import com.miqu3iasg.banking.boleto.config.EfiBoletoProperties;
import com.miqu3iasg.banking.boleto.domain.Address;
import com.miqu3iasg.banking.boleto.gateway.EfiBoletoAuthGateway;
import com.miqu3iasg.banking.boleto.gateway.EfiBoletoGateway;
import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import com.miqu3iasg.banking.pix.gateway.EfiEvpGateway;
import com.miqu3iasg.banking.pix.gateway.EfiPixAuthGateway;
import com.miqu3iasg.banking.pix.gateway.EfiPixGateway;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
import com.miqu3iasg.banking.pix.service.PixExpirationScheduler;
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

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@Testcontainers
@SpringBootTest(
	classes = BankingMvpApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("e2e-test")
public abstract class AbstractE2eTestSupport {

	protected static final String PIX_KEY = System.getenv().get("EFI_PIX_KEY");

	protected static final String CPF_1 = "52998224725";
	protected static final String CPF_2 = "87748248800";
	protected static final String CNPJ_VALID = "11222333000181";

	/**
	 * Formatted; only for normalisation tests
	 **/
	protected static final String CPF_2_FORMATTED = "111.444.777-35";
	protected static final String CPF_3_FORMATTED = "222.333.444-05";

	protected static final BigDecimal AMOUNT_BOLETO_MIN = new BigDecimal("5.00");
	protected static final BigDecimal AMOUNT_MIN = new BigDecimal("0.01");
	protected static final BigDecimal AMOUNT_1_00 = new BigDecimal("1.00");
	protected static final BigDecimal AMOUNT_100 = new BigDecimal("100.00");
	protected static final BigDecimal AMOUNT_123_45 = new BigDecimal("123.45");
	protected static final BigDecimal AMOUNT_200 = new BigDecimal("200.00");
	protected static final BigDecimal AMOUNT_300 = new BigDecimal("300.00");
	protected static final BigDecimal AMOUNT_500 = new BigDecimal("500.00");
	protected static final BigDecimal AMOUNT_LARGE = new BigDecimal("9999.99");

	protected static final BigDecimal STANDARD = AMOUNT_500;

	/**
	 * Reusable payer address for boleto E2E tests.
	 * Matches the address used in {@code efiBoletoJson} so HTTP-level and gateway-level
	 * tests exercise the same payer data.
	 */
	protected static final Address BOLETO_PAYER_ADDRESS = Address.of(
		"Avenida Juscelino Kubitschek",
		"909",
		"Bauxita",
		"35400000",
		"Ouro Preto",
		"MG"
	);

	@Autowired
	protected AccountService accountService;
	@Autowired
	protected AccountRepository accountRepository;
	@Autowired
	protected PixKeyRepository keyRepository;
	@Autowired
	protected PixChargeRepository chargeRepository;
	@Autowired
	protected PixExpirationScheduler expirationJob;
	@Autowired
	protected TransactionTemplate txTemplate;
	@Autowired
	protected CacheManager cacheManager;
	@Autowired
	protected EfiPixProperties pixProperties;
	@Autowired
	protected EfiBoletoProperties boletoProperties;
	@Autowired
	protected EfiPixGateway efiPixGateway;
	@Autowired
	protected EfiBoletoGateway efiBoletoGateway;
	@Autowired
	protected EfiPixAuthGateway efiPixAuthGateway;
	@Autowired
	protected EfiBoletoAuthGateway efiBoletoAuthGateway;
	@Autowired
	protected ObjectMapper objectMapper;
	@Autowired
	protected EfiEvpGateway evpGateway;

	@Autowired
	@Qualifier("efiPixWebClient")
	protected WebClient efiPixWebClient;

	@Autowired
	@Qualifier("efiBoletoWebClient")
	protected WebClient efiBoletoWebClient;

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

	protected WebTestClient pixSandboxClient () {
		String token = efiPixAuthGateway.getAccessToken();
		return buildEfiPixSandboxProxyClient("Bearer " + token, true);
	}

	protected WebTestClient boletoSandboxClient () {
		String token = efiBoletoAuthGateway.getAccessToken();
		return buildEfiBoletoSandboxProxyClient("Bearer " + token, true);
	}

	protected WebTestClient unauthenticatedBearerPixSandboxClient () {
		return buildEfiPixSandboxProxyClient("Bearer invalid_token", false);
	}

	protected WebTestClient unauthenticatedBearerBoletoSandboxClient () {
		return buildEfiBoletoSandboxProxyClient("Bearer deliberately_invalid_token", false);
	}

	protected WebTestClient unauthenticatedBasicPixSandboxClient () {
		return buildEfiPixSandboxProxyClient("Basic aW52YWxpZDppbnZhbGlk", false);
	}

	protected WebTestClient buildEfiPixSandboxProxyClient (String authorizationHeader, boolean authenticated) {
		return buildEfiSandboxProxyClient(efiPixWebClient, authorizationHeader, authenticated);
	}

	protected WebTestClient buildEfiBoletoSandboxProxyClient (String authorizationHeader, boolean authenticated) {
		return buildEfiSandboxProxyClient(efiBoletoWebClient, authorizationHeader, authenticated);
	}

	private WebTestClient buildEfiSandboxProxyClient (
		WebClient webClient,
		String authorizationHeader,
		boolean authenticated
	) {
		return WebTestClient
			.bindToWebHandler(exchange -> {
				URI requestUri = exchange.getRequest().getURI();
				String pathAndQuery = requestUri.getRawPath()
					+ (requestUri.getRawQuery() != null ? "?" + requestUri.getRawQuery() : "");

				var client = webClient
					.mutate()
					.defaultHeader("Authorization", authorizationHeader)
					.defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.build()
					.method(exchange.getRequest().getMethod())
					.uri(pathAndQuery)
					.headers(h -> h.addAll(exchange.getRequest().getHeaders()))
					.body(exchange.getRequest().getBody(), DataBuffer.class);

				if (authenticated) {
					return client
						.retrieve()
						.toEntity(DataBuffer.class)
						.flatMap(entity -> {
							exchange.getResponse().setStatusCode(entity.getStatusCode());
							exchange.getResponse().getHeaders().addAll(entity.getHeaders());
							return exchange.getResponse().writeWith(Mono.justOrEmpty(entity.getBody()));
						});
				} else {
					return client
						.exchangeToMono(response -> {
							exchange.getResponse().setStatusCode(response.statusCode());
							exchange.getResponse().getHeaders().addAll(response.headers().asHttpHeaders());
							return response.bodyToMono(DataBuffer.class)
								.flatMap(body -> exchange.getResponse().writeWith(Mono.just(body)))
								.switchIfEmpty(exchange.getResponse().setComplete());
						});
				}
			})
			.build();
	}

	@BeforeEach
	void cleanCharges () {
		chargeRepository.deleteAll();
	}

	@BeforeEach
	void evictAllOAuthCaches () {
		evictCache("efi-oauth-token");
		evictCache("efi-cobrancas-oauth-token");
	}

	private void evictCache (String cacheName) {
		var cache = cacheManager.getCache(cacheName);
		if (cache != null) cache.clear();
	}

	protected String generateTxid () {
		return UUID.randomUUID().toString()
			.replace("-", "")
			.substring(0, BacenSpec.TXID_LENGTH)
			.toUpperCase();
	}
}
