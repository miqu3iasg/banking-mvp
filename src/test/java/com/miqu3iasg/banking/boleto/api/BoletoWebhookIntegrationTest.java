package com.miqu3iasg.banking.boleto.api;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.boleto.domain.Boleto;
import com.miqu3iasg.banking.boleto.domain.BoletoStatus;
import com.miqu3iasg.banking.boleto.gateway.BoletoGateway;
import com.miqu3iasg.banking.boleto.repository.BoletoRepository;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import com.miqu3iasg.banking_mvp.shared.support.AbstractIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BoletoWebhookIntegrationTest extends AbstractIntegrationTestSupport {

	@LocalServerPort
	private int port;

	private WebTestClient webTestClient;

	@Autowired
	private BoletoRepository boletoRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@MockitoBean
	private BoletoGateway boletoGateway;

	private UUID accountId;
	private Boleto boleto;
	private final String VALID_IP = "34.193.116.226";

	@BeforeEach
	void setUp () {
		webTestClient = WebTestClient.bindToServer()
			.baseUrl("http://localhost:" + port)
			.build();

		transactionRepository.deleteAll();
		boletoRepository.deleteAll();
		accountRepository.deleteAll();

		var accountResponse = openChecking(CPF_1);
		this.accountId = accountResponse.id();

		Boleto b = Boleto.issue(
			accountId,
			"John Doe",
			"12345678900",
			null,
			new BigDecimal("100.00"),
			java.time.LocalDate.now().plusDays(1),
			"Test Boleto"
		);
		b.enrichWithProviderData(12345L, "barcode", "link", "pdf");
		this.boleto = boletoRepository.save(b);
	}

	@Test
	@DisplayName("processPayment_creditsAccount_whenProviderConfirmsPaid")
	void processPaymentCreditsAccountWhenProviderConfirmsPaid () {
		long chargeId = boleto.getProviderChargeId();
		BigDecimal balanceBefore = accountRepository.findById(accountId).get().getBalance().amount();

		when(boletoGateway.getChargeStatus(chargeId)).thenReturn("paid");

		String payload = String.format("{\"providerChargeId\": %d, \"receivedAt\": \"%s\"}",
			chargeId, Instant.now().toString());

		webTestClient
			.post()
			.uri("/v1/boleto/webhook/payment")
			.header("X-Forwarded-For", VALID_IP)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(payload)
			.exchange()
			.expectStatus().isOk();

		Boleto updatedBoleto = boletoRepository.findById(boleto.getId()).get();
		assertThat(updatedBoleto.getStatus()).isEqualTo(BoletoStatus.PAID);

		Account updatedAccount = accountRepository.findById(accountId).get();
		assertThat(updatedAccount.getBalance().amount())
			.isEqualByComparingTo(balanceBefore.add(new BigDecimal("100.00")));

		assertThat(transactionRepository.findByAccountId(accountId)).hasSize(1);
	}

	@Test
	@DisplayName("processPayment_ignores_whenBoletoAlreadyPaid")
	void processPaymentIgnoresWhenBoletoAlreadyPaid () {
		boleto.markAsPaid(Instant.now());
		boletoRepository.save(boleto);

		BigDecimal balanceBefore = accountRepository.findById(accountId).get().getBalance().amount();
		long chargeId = boleto.getProviderChargeId();

		String payload = String.format("{\"providerChargeId\": %d, \"receivedAt\": \"%s\"}",
			chargeId, Instant.now().toString());

		webTestClient.post().uri("/v1/boleto/webhook/payment")
			.header("X-Forwarded-For", VALID_IP)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(payload)
			.exchange()
			.expectStatus().isOk();

		Account updatedAccount = accountRepository.findById(accountId).get();
		assertThat(updatedAccount.getBalance().amount()).isEqualByComparingTo(balanceBefore);
		assertThat(transactionRepository.findByAccountId(accountId)).isEmpty();
	}

	@Test
	@DisplayName("processPayment_isIdempotent_whenSameWebhookReceivedTwice")
	void processPaymentIsIdempotentWhenSameWebhookReceivedTwice () {
		long chargeId = boleto.getProviderChargeId();
		BigDecimal balanceBefore = accountRepository.findById(accountId).get().getBalance().amount();

		when(boletoGateway.getChargeStatus(chargeId)).thenReturn("paid");

		String payload = String.format("{\"providerChargeId\": %d, \"receivedAt\": \"%s\"}",
			chargeId, Instant.now().toString());

		webTestClient.post().uri("/v1/boleto/webhook/payment")
			.header("X-Forwarded-For", VALID_IP)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(payload)
			.exchange()
			.expectStatus().isOk();

		webTestClient.post().uri("/v1/boleto/webhook/payment")
			.header("X-Forwarded-For", VALID_IP)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(payload)
			.exchange()
			.expectStatus().isOk();

		Account updatedAccount = accountRepository.findById(accountId).get();
		assertThat(updatedAccount.getBalance().amount())
			.isEqualByComparingTo(balanceBefore.add(new BigDecimal("100.00")));

		assertThat(transactionRepository.findByAccountId(accountId)).hasSize(1);
	}
}
