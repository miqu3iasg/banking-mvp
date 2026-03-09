package com.miqu3iasg.banking_mvp.efi.pix.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.pix.api.dto.CreatePixChargeRequest;
import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.domain.PixChargeStatus;
import com.miqu3iasg.banking.pix.domain.PixKey;
import com.miqu3iasg.banking.pix.domain.PixKeyType;
import com.miqu3iasg.banking.pix.exception.InvalidPixStateTransitionException;
import com.miqu3iasg.banking.pix.exception.PixChargeNotFoundException;
import com.miqu3iasg.banking.pix.exception.PixGatewayException;
import com.miqu3iasg.banking.pix.exception.PixKeyNotFoundException;
import com.miqu3iasg.banking.pix.gateway.PixChargeResponse;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking_mvp.shared.support.AbstractIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixServiceIntegrationTest extends AbstractIntegrationTestSupport {

	private static final String PIX_KEY_VALUE = "test-pix-key";

	private static final String TOKEN_RESPONSE = """
		{
		  "access_token": "test-access-token",
		  "token_type":   "Bearer",
		  "expires_in":   3600
		}
		""";

	private static final String CREATE_CHARGE_RESPONSE = """
		{
		  "txid":          "ABC123DEF456GHI789JKL01234",
		  "revisao":       0,
		  "status":        "ATIVA",
		  "calendario":    { "criacao": "2025-06-15T14:30:00.000Z", "expiracao": 3600 },
		  "devedor":       { "cpf": "52998224725", "nome": "Francisco da Silva" },
		  "valor":         { "original": "150.00" },
		  "chave":         "test-pix-key",
		  "loc":           { "id": 1, "location": "qrcodespix-h.sejaefi.com.br/v2/abc123", "tipoCob": "cob" },
		  "pixCopiaECola": "00020101021226830014BR.GOV.BCB.PIX2561qrcodespix.sejaefi.com.br/v2/41e0badf811a4ce6ad8a80b306821fce5204000053000065802BR5905EFISA6008SAOPAULO60070503***61040000"
		}
		""";

	private static final String CREATE_CHARGE_RESPONSE_WITH_DIFFERENT_TXID = """
		{
		  "txid":          "ZZZ999XXX888YYY777000111AB",
		  "revisao":       0,
		  "status":        "ATIVA",
		  "calendario":    { "criacao": "2025-06-15T14:30:00.000Z", "expiracao": 3600 },
		  "devedor":       { "cpf": "52998224725", "nome": "Francisco da Silva" },
		  "valor":         { "original": "50.00" },
		  "chave":         "test-pix-key",
		  "loc":           { "id": 2, "location": "qrcodespix-h.sejaefi.com.br/v2/def456", "tipoCob": "cob" },
		  "pixCopiaECola": "00020101021226830014BR.GOV.BCB.PIX2561qrcodespix.sejaefi.com.br/v2/def456"
		}
		""";

	private static final String GET_CHARGE_RESPONSE = """
		{
		  "txid":          "ABC123DEF456GHI789JKL01234",
		  "status":        "ATIVA",
		  "pixCopiaECola": "00020101021226830014BR.GOV.BCB.PIX2561qrcodespix.sejaefi.com.br/v2/41e0badf811a4ce6ad8a80b306821fce5204000053000065802BR5905EFISA6008SAOPAULO60070503***61040000"
		}
		""";

	private static final String CHARGE_NOT_FOUND_RESPONSE = """
		{
		  "nome":     "cobranca_nao_encontrada",
		  "mensagem": "Nenhuma cobrança encontrada com o txid informado."
		}
		""";

	private static final String UNAUTHORIZED_RESPONSE = """
		{
		  "nome":     "nao_autorizado",
		  "mensagem": "Token de acesso inválido ou expirado."
		}
		""";

	private static final String GATEWAY_ERROR_RESPONSE = """
		{
		  "nome":     "erro_aplicacao",
		  "mensagem": "Ocorreu um erro ao processar a cobrança."
		}
		""";

	static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

	@DynamicPropertySource
	static void overrideEfiProperties (DynamicPropertyRegistry registry) {
		wireMock.start();

		WireMock.configureFor(wireMock.port());

		registry.add("efi.pix.client-id", () -> "test-client-id");
		registry.add("efi.pix.client-secret", () -> "test-client-secret");
		registry.add("efi.pix.certificate-path", () -> "classpath:test-cert.p12");
		registry.add("efi.pix.certificate-password", () -> "changeit");
		registry.add("efi.pix.sandbox", () -> "true");
		registry.add("efi.pix.charge-expires-in-seconds", () -> "3600");
		registry.add("efi.pix.response-timeout-in-seconds", () -> "10");
		registry.add("efi.pix.webhook-url", () -> "http://localhost/webhook");
		registry.add("efi.pix.base-url-override", wireMock::baseUrl);
	}

	@AfterAll
	static void stopWireMock () {
		wireMock.stop();
	}

	private UUID accountId;
	private String idempotencyKey;

	@BeforeEach
	void setUpPixFixtures () {
		var cache = cacheManager.getCache("efi-oauth-token");
		if (cache != null) cache.clear();

		transactionRepository.deleteAll();
		chargeRepository.deleteAll();
		keyRepository.deleteAll();
		accountRepository.deleteAll();

		accountId = openChecking(CPF_1).id();
		idempotencyKey = "idem:" + UUID.randomUUID();

		PixKey key = PixKey.register(accountId, PixKeyType.RANDOM, PIX_KEY_VALUE);
		keyRepository.save(key);

		stubFor(post(urlEqualTo("/oauth/token"))
			.willReturn(aResponse()
				.withStatus(HttpStatus.OK.value())
				.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.withBody(TOKEN_RESPONSE)));

		stubFor(put(urlPathMatching("/v2/cob/.*"))
			.willReturn(aResponse()
				.withStatus(HttpStatus.CREATED.value())
				.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.withBody(CREATE_CHARGE_RESPONSE)));

		stubFor(get(urlPathMatching("/v2/cob/.*"))
			.willReturn(aResponse()
				.withStatus(HttpStatus.OK.value())
				.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.withBody(GET_CHARGE_RESPONSE)));

		stubFor(patch(urlPathMatching("/v2/cob/.*"))
			.willReturn(aResponse()
				.withStatus(HttpStatus.OK.value())
				.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.withBody("{}")));

		stubFor(put(urlPathMatching("/v2/webhook/.*"))
			.willReturn(aResponse()
				.withStatus(HttpStatus.NO_CONTENT.value())));
	}

	@AfterEach
	void resetWireMock () {
		wireMock.resetAll();
	}

	@Nested
	@DisplayName("Creating a PIX charge")
	class CreateCharge {

		@Test
		@DisplayName("persists the charge with PENDING status and enriches it with the provider's QR code data")
		void createChargePersistsChargeAndEnrichesWithProviderData () {
			PixChargeResponse response = createCharge(new BigDecimal("150.00"));

			PixCharge stored = chargeRepository.findByTxid(response.txid()).orElseThrow();

			assertThat(stored.getStatus()).isEqualTo(PixChargeStatus.PENDING);
			assertThat(stored.getAccountId()).isEqualTo(accountId);
			assertThat(stored.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
			assertThat(stored.getCopyPaste()).startsWith("00020101");
			assertThat(stored.getQrCode()).isNotBlank();
			assertThat(stored.getPayerName()).isEqualTo("John Doe");
			assertThat(stored.getPayerCpfCnpj()).isEqualTo("52998224725");
			assertThat(stored.getExpiresAt()).isAfter(Instant.now());
		}

		@Test
		@DisplayName("sends a correctly formed PUT /v2/cob/{txid} request to the Efí Bank API")
		void createChargeSendsCorrectlyFormedRequestToGateway () {
			createCharge(new BigDecimal("150.00"));

			verify(putRequestedFor(urlPathMatching("/v2/cob/.*"))
				.withHeader(HttpHeaders.AUTHORIZATION, matching("Bearer .+"))
				.withRequestBody(matchingJsonPath("$.calendario.expiracao"))
				.withRequestBody(matchingJsonPath("$.valor.original"))
				.withRequestBody(matchingJsonPath("$.chave")));
		}

		@Test
		@DisplayName("obtains an OAuth2 token before calling the charge endpoint")
		void createChargeObtainsOAuthTokenBeforeCallingGateway () {
			createCharge(new BigDecimal("150.00"));

			verify(postRequestedFor(urlEqualTo("/oauth/token")));
			verify(putRequestedFor(urlPathMatching("/v2/cob/.*"))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-access-token")));
		}

		@Test
		@DisplayName("caches the OAuth2 token so subsequent charge creations do not re-fetch it")
		void createChargeCachesOAuthTokenAcrossSubsequentCalls () {
			createCharge(new BigDecimal("150.00"));

			wireMock.resetAll();

			stubFor(put(urlPathMatching("/v2/cob/.*"))
				.willReturn(aResponse()
					.withStatus(HttpStatus.CREATED.value())
					.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.withBody(CREATE_CHARGE_RESPONSE_WITH_DIFFERENT_TXID)));

			pixService.createCharge(accountId, chargeRequest(new BigDecimal("50.00")), "key-2");

			verify(0, postRequestedFor(urlEqualTo("/oauth/token")));
		}

		@Test
		@DisplayName("evicts the cached token and transparently retries when the gateway returns 401")
		void createChargeEvictsStaleCachedTokenAndRetriesAfter401 () {
			var cache = cacheManager.getCache("efi-oauth-token");
			assertThat(cache).isNotNull();
			cache.put("access_token", "stale-token");

			stubFor(put(urlPathMatching("/v2/cob/.*"))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer stale-token"))
				.willReturn(aResponse()
					.withStatus(HttpStatus.UNAUTHORIZED.value())
					.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.withBody(UNAUTHORIZED_RESPONSE)));

			stubFor(put(urlPathMatching("/v2/cob/.*"))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-access-token"))
				.willReturn(aResponse()
					.withStatus(HttpStatus.CREATED.value())
					.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.withBody(CREATE_CHARGE_RESPONSE)));

			PixChargeResponse response = createCharge(new BigDecimal("150.00"));

			assertThat(response.txid()).isNotBlank();
			assertThat(cache.get("access_token")).isNotNull();
			assertThat(cache.get("access_token").get()).isNotEqualTo("stale-token");
		}

		@Test
		@DisplayName("throws PixGatewayException and does not persist a charge when the Efí Bank API returns a 500")
		void createChargeThrowsAndDoesNotPersistWhenGatewayReturns500 () {
			stubFor(put(urlPathMatching("/v2/cob/.*"))
				.willReturn(aResponse()
					.withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
					.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.withBody(GATEWAY_ERROR_RESPONSE)));

			assertThatThrownBy(() -> createCharge(new BigDecimal("150.00")))
				.isInstanceOf(PixGatewayException.class);

			assertThat(chargeRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).isEmpty();
		}

		@Test
		@DisplayName("replaying the same idempotency key returns the cached response without re-calling the Efí Bank API")
		void createChargeReplayWithSameKeyReturnsCachedResponseAndSkipsGateway () {
			PixChargeResponse first = createCharge(new BigDecimal("150.00"));
			PixChargeResponse second = pixService.createCharge(accountId, chargeRequest(new BigDecimal("150.00")), idempotencyKey);

			assertThat(second.txid()).isEqualTo(first.txid());
			assertThat(second.copyPaste()).isEqualTo(first.copyPaste());
			verify(1, putRequestedFor(urlPathMatching("/v2/cob/.*")));
		}

		@Test
		@DisplayName("replaying an idempotent request does not insert a second row in the database")
		void createChargeIdempotentReplayDoesNotDuplicatePersistedCharge () {
			createCharge(new BigDecimal("150.00"));
			pixService.createCharge(accountId, chargeRequest(new BigDecimal("150.00")), idempotencyKey);

			assertThat(chargeRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).hasSize(1);
		}

		@Test
		@DisplayName("throws PixKeyNotFoundException when the account has no active PIX key, without calling the gateway")
		void createChargeThrowsWhenNoActivePixKeyExistsWithoutCallingGateway () {
			keyRepository.deleteAll();

			assertThatThrownBy(() -> createCharge(new BigDecimal("150.00")))
				.isInstanceOf(PixKeyNotFoundException.class);

			verify(0, putRequestedFor(urlPathMatching("/v2/cob/.*")));
		}

		@Test
		@DisplayName("concurrent requests with the same idempotency key produce exactly one persisted charge and one call to the gateway")
		void createChargeConcurrentRequestsWithSameKeyProduceExactlyOneChargeAndOneGatewayCall () throws Exception {
			int threads = 8;
			CountDownLatch ready = new CountDownLatch(threads);
			CountDownLatch start = new CountDownLatch(1);
			AtomicInteger successes = new AtomicInteger();
			CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();

			try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
				List<Future<Void>> futures = IntStream.range(0, threads)
					.mapToObj(i -> pool.submit((Callable<Void>) () -> {
						ready.countDown();
						start.await();
						try {
							pixService.createCharge(accountId, chargeRequest(new BigDecimal("150.00")), idempotencyKey);
							successes.incrementAndGet();
						} catch (Exception e) {
							failures.add(e);
						}
						return null;
					}))
					.toList();

				ready.await();
				start.countDown();
				for (Future<Void> f : futures) f.get(30, TimeUnit.SECONDS);
			}

			assertThat(failures).isEmpty();
			assertThat(successes.get()).isEqualTo(threads);
			assertThat(chargeRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).hasSize(1);
			verify(1, putRequestedFor(urlPathMatching("/v2/cob/.*")));
		}
	}

	@Nested
	@DisplayName("Retrieving a PIX charge")
	class GetCharge {

		@Test
		@DisplayName("returns the correct DTO for a charge that belongs to the requested account")
		void getChargeReturnsCorrectDtoForExistingTxid () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));

			PixChargeResponse fetched = pixService.getCharge(created.txid(), accountId);

			assertThat(fetched.txid()).isEqualTo(created.txid());
			assertThat(fetched.status()).isEqualTo(PixChargeStatus.PENDING.name());
		}

		@Test
		@DisplayName("throws PixChargeNotFoundException when the txid does not exist in the database")
		void getChargeThrowsForUnknownTxid () {
			assertThatThrownBy(() -> pixService.getCharge("UNKNOWNTXID0000000000000001", accountId))
				.isInstanceOf(PixChargeNotFoundException.class);
		}

		@Test
		@DisplayName("throws PixChargeNotFoundException when the txid belongs to a different account")
		void getChargeThrowsWhenOwnershipMismatch () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));

			assertThatThrownBy(() -> pixService.getCharge(created.txid(), UUID.randomUUID()))
				.isInstanceOf(PixChargeNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("Cancelling a PIX charge")
	class CancelCharge {

		@Test
		@DisplayName("transitions the charge to CANCELLED and sends PATCH /v2/cob/{txid} to the Efí Bank API")
		void cancelChargePersistsCancelledStatusAndCallsGateway () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));

			pixService.cancelCharge(created.txid(), accountId);

			assertThat(chargeRepository.findByTxid(created.txid()).orElseThrow().getStatus())
				.isEqualTo(PixChargeStatus.CANCELLED);

			verify(patchRequestedFor(urlPathMatching("/v2/cob/.*"))
				.withRequestBody(matchingJsonPath("$.status", equalTo("REMOVIDA_PELO_USUARIO_RECEBEDOR"))));
		}

		@Test
		@DisplayName("throws InvalidPixStateTransitionException when attempting to cancel an already CANCELLED charge")
		void cancelAlreadyCancelledChargeThrows () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));
			pixService.cancelCharge(created.txid(), accountId);

			assertThatThrownBy(() -> pixService.cancelCharge(created.txid(), accountId))
				.isInstanceOf(InvalidPixStateTransitionException.class);
		}

		@Test
		@DisplayName("throws PixChargeNotFoundException when the txid to cancel does not exist in the database")
		void cancelChargeThrowsForUnknownTxid () {
			assertThatThrownBy(() -> pixService.cancelCharge("UNKNOWNTXID0000000000000001", accountId))
				.isInstanceOf(PixChargeNotFoundException.class);
		}

		@Test
		@DisplayName("throws PixChargeNotFoundException when the charge belongs to a different account and leaves the charge PENDING")
		void cancelChargeThrowsOnOwnershipMismatchAndLeavesChargePending () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));

			assertThatThrownBy(() -> pixService.cancelCharge(created.txid(), UUID.randomUUID()))
				.isInstanceOf(PixChargeNotFoundException.class);

			assertThat(chargeRepository.findByTxid(created.txid()).orElseThrow().getStatus())
				.isEqualTo(PixChargeStatus.PENDING);
		}
	}

	@Nested
	@DisplayName("Processing a webhook payment")
	class ProcessWebhookPayment {

		@Test
		@DisplayName("marks the charge as PAID, stores the paidAt timestamp, and credits the account by the exact charge amount")
		void processWebhookMarksPaidAndCreditsAccountBalance () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));
			Instant paidAt = Instant.parse("2025-06-15T14:30:00Z");

			BigDecimal balanceBefore = loadAccount().getBalance().amount();

			pixService.processWebhookPayment(created.txid(), paidAt, webhookKey());

			PixCharge stored = chargeRepository.findByTxid(created.txid()).orElseThrow();
			assertThat(stored.getStatus()).isEqualTo(PixChargeStatus.PAID);
			assertThat(stored.getPaidAt()).isEqualTo(paidAt);
			assertThat(loadAccount().getBalance().amount())
				.isEqualByComparingTo(balanceBefore.add(new BigDecimal("150.00")));
		}

		@Test
		@DisplayName("persists a Transaction record for the credit with a description referencing the txid")
		void processWebhookPersistsTransactionWithCorrectAmountAndTxidReference () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));

			pixService.processWebhookPayment(created.txid(), Instant.now(), webhookKey());

			var transactions = transactionRepository.findByAccountId(accountId);
			assertThat(transactions).hasSize(1);
			assertThat(transactions.get(0).getDescription()).contains(created.txid());
			assertThat(transactions.get(0).getAmount().amount()).isEqualByComparingTo(new BigDecimal("150.00"));
		}

		@Test
		@DisplayName("replaying the same webhook idempotency key does not credit the account a second time or persist a duplicate transaction")
		void processWebhookIsIdempotentAndDoesNotDoubleCredit () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));
			String key = webhookKey();

			pixService.processWebhookPayment(created.txid(), Instant.now(), key);
			BigDecimal balanceAfterFirst = loadAccount().getBalance().amount();

			pixService.processWebhookPayment(created.txid(), Instant.now(), key);

			assertThat(loadAccount().getBalance().amount()).isEqualByComparingTo(balanceAfterFirst);
			assertThat(transactionRepository.findByAccountId(accountId)).hasSize(1);
		}

		@Test
		@DisplayName("throws PixChargeNotFoundException when the webhook references a txid absent from the database")
		void processWebhookThrowsForUnknownTxid () {
			assertThatThrownBy(() ->
				pixService.processWebhookPayment("UNKNOWNTXID0000000000000001", Instant.now(), webhookKey())
			).isInstanceOf(PixChargeNotFoundException.class);
		}

		@Test
		@DisplayName("throws AccountNotFoundException when the charge's owner account no longer exists")
		void processWebhookThrowsWhenAccountHasBeenDeleted () {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));

			transactionRepository.deleteAll();
			accountRepository.deleteById(accountId);

			assertThatThrownBy(() ->
				pixService.processWebhookPayment(created.txid(), Instant.now(), webhookKey())
			).isInstanceOf(AccountNotFoundException.class);
		}

		@Test
		@DisplayName("concurrent webhook deliveries for the same txid credit the account exactly once regardless of race conditions")
		void processWebhookConcurrentDeliveriesForSameTxidCreditExactlyOnce () throws Exception {
			PixChargeResponse created = createCharge(new BigDecimal("150.00"));
			String key = "webhook:" + created.txid();
			Instant paidAt = Instant.now();
			BigDecimal balanceBefore = loadAccount().getBalance().amount();

			int threads = 6;
			CountDownLatch ready = new CountDownLatch(threads);
			CountDownLatch start = new CountDownLatch(1);
			CopyOnWriteArrayList<Throwable> unexpected = new CopyOnWriteArrayList<>();

			try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
				List<Future<Void>> futures = IntStream.range(0, threads)
					.mapToObj(i -> pool.submit((Callable<Void>) () -> {
						ready.countDown();
						start.await();
						try {
							pixService.processWebhookPayment(created.txid(), paidAt, key);
						} catch (InvalidPixStateTransitionException ignored) {
						} catch (Exception e) {
							unexpected.add(e);
						}
						return null;
					}))
					.toList();

				ready.await();
				start.countDown();
				for (Future<Void> f : futures) f.get(30, TimeUnit.SECONDS);
			}

			assertThat(unexpected).isEmpty();
			assertThat(loadAccount().getBalance().amount())
				.isEqualByComparingTo(balanceBefore.add(new BigDecimal("150.00")));
		}
	}

	private PixChargeResponse createCharge (BigDecimal amount) {
		return pixService.createCharge(accountId, chargeRequest(amount), idempotencyKey);
	}

	private CreatePixChargeRequest chargeRequest (BigDecimal amount) {
		return new CreatePixChargeRequest(amount, "John Doe", "52998224725");
	}

	private Account loadAccount () {
		return accountRepository.findById(accountId).orElseThrow();
	}

	private String webhookKey () {
		return "webhook:" + UUID.randomUUID();
	}
}
