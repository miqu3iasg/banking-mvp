package com.miqu3iasg.banking.pix.api;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.domain.PixChargeStatus;
import com.miqu3iasg.banking.pix.domain.PixKey;
import com.miqu3iasg.banking.pix.domain.PixKeyType;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import com.miqu3iasg.banking_mvp.shared.support.AbstractIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PixWebhookIntegrationTest extends AbstractIntegrationTestSupport {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PixChargeRepository chargeRepository;

    @Autowired
    private PixKeyRepository keyRepository;

    private UUID accountId;
    private PixCharge pixCharge;
    private final String VALID_IP = "34.193.116.226";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        transactionRepository.deleteAll();
        chargeRepository.deleteAll();
        keyRepository.deleteAll();
        accountRepository.deleteAll();

        var accountResponse = openChecking(CPF_1);
        this.accountId = accountResponse.id();

        PixKey key = PixKey.register(accountId, PixKeyType.RANDOM, "test-pix-key-" + UUID.randomUUID());
        keyRepository.save(key);

        pixCharge = PixCharge.create(
                accountId,
                new BigDecimal("100.00"),
                "John Doe",
                "52998224725",
                "test-txid-12345",
                3600
        );
        pixCharge.enrichWithProviderData("test-txid-12345", "test-qr-code", "test-copy-paste");
        pixCharge = chargeRepository.save(pixCharge);
    }

    @Test
    @DisplayName("processPayment_creditsAccount_whenPixChargeIsPaid")
    void processPaymentCreditsAccountWhenPixChargeIsPaid() {
        String txid = pixCharge.getTxid();
        BigDecimal balanceBefore = accountRepository.findById(accountId).get().getBalance().amount();

        String payload = String.format(
                "{\"payments\": [{\"txid\": \"%s\", \"endToEndId\": \"E12345678901234567890123456789\", \"timestamp\": \"%s\"}]}",
                txid,
                Instant.now().toString()
        );

        webTestClient
                .post()
                .uri("/v1/pix/webhook/pix")
                .header("X-Forwarded-For", VALID_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk();

        PixCharge updatedCharge = chargeRepository.findByTxid(txid).orElseThrow();
        assertThat(updatedCharge.getStatus()).isEqualTo(PixChargeStatus.PAID);

        Account updatedAccount = accountRepository.findById(accountId).get();
        assertThat(updatedAccount.getBalance().amount())
                .isEqualByComparingTo(balanceBefore.add(new BigDecimal("100.00")));

        assertThat(transactionRepository.findByAccountId(accountId)).hasSize(1);
    }

    @Test
    @DisplayName("processPayment_ignores_whenChargeAlreadyPaid")
    void processPaymentIgnoresWhenChargeAlreadyPaid() {
        pixCharge.markAsPaid(Instant.now());
        chargeRepository.save(pixCharge);

        BigDecimal balanceBefore = accountRepository.findById(accountId).get().getBalance().amount();
        String txid = pixCharge.getTxid();

        String payload = String.format(
                "{\"payments\": [{\"txid\": \"%s\", \"endToEndId\": \"E12345678901234567890123456789\", \"timestamp\": \"%s\"}]}",
                txid,
                Instant.now().toString()
        );

        webTestClient.post().uri("/v1/pix/webhook/pix")
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
    void processPaymentIsIdempotentWhenSameWebhookReceivedTwice() {
        String txid = pixCharge.getTxid();
        BigDecimal balanceBefore = accountRepository.findById(accountId).get().getBalance().amount();

        String payload = String.format(
                "{\"payments\": [{\"txid\": \"%s\", \"endToEndId\": \"E12345678901234567890123456789\", \"timestamp\": \"%s\"}]}",
                txid,
                Instant.now().toString()
        );

        webTestClient.post().uri("/v1/pix/webhook/pix")
                .header("X-Forwarded-For", VALID_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/v1/pix/webhook/pix")
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
