package com.miqu3iasg.banking_mvp.efi.boleto;

import com.miqu3iasg.banking.boleto.exception.BoletoGatewayException;
import com.miqu3iasg.banking.boleto.gateway.BoletoIssuanceRequest;
import com.miqu3iasg.banking.boleto.gateway.BoletoIssuanceResponse;
import com.miqu3iasg.banking.shared.exception.TransientExceptionClassifier;
import com.miqu3iasg.banking_mvp.shared.support.AbstractE2eTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EfiBoletoGatewayE2eTest extends AbstractE2eTestSupport {

	private static final String EFI_BOLETO_ONE_STEP_INITIAL_STATUS_WAITING = "waiting";
	private static final String EFI_ERROR_EXCEEDS_OPERATIONAL_LIMIT = "4600037";

	private static final int FEBRABAN_STANDARD_BARCODE_NUMERIC_LENGTH = 44;

	private static final String DESCRIPTION = "E2E Boleto Test";
	private static final LocalDate DUE_DATE = LocalDate.now().plusDays(3);

	private final List<Long> issuedChargeIds = new ArrayList<>();

	@Test
	@DisplayName("issuing a boleto must return a positive provider chargeId assigned by Efí Bank")
	void issueReturnsPositiveProviderChargeId () {
		BoletoIssuanceResponse response = issue(STANDARD);

		assertThat(response.providerChargeId()).isPositive();
	}

	@Test
	@DisplayName("barcode must be a non-blank numeric string of at least 44 characters conforming to the Febraban standard")
	void issueReturnsFebrabanCompliantBarcode () {
		BoletoIssuanceResponse response = issue(STANDARD);

		String rawBarcode = response.barcode().replace(" ", "");

		assertThat(response.barcode()).isNotBlank();
		assertThat(rawBarcode)
			.matches("\\d+")
			.hasSizeGreaterThanOrEqualTo(FEBRABAN_STANDARD_BARCODE_NUMERIC_LENGTH);
	}

	@Test
	@DisplayName("billetLink must be a non-blank HTTPS URL pointing to an Efí-hosted Bolix page")
	void issueReturnsBilletLinkThatIsHttpsUrl () {
		BoletoIssuanceResponse response = issue(STANDARD);

		assertThat(response.billetLink())
			.isNotBlank()
			.startsWith("https://")
			.doesNotContainAnyWhitespaces()
			.containsAnyOf("gerencianet.com.br", "efipay.com.br", "sejaefi.com.br");
	}

	@Test
	@DisplayName("pdfUrl must be a non-blank HTTPS URL; Efí Sandbox always populates pdf.charge")
	void issueReturnsPdfUrlThatIsNonBlankHttpsUrl () {
		BoletoIssuanceResponse response = issue(STANDARD);

		assertThat(response.pdfUrl())
			.isNotBlank()
			.startsWith("https://")
			.doesNotContainAnyWhitespaces();
	}

	@Test
	@DisplayName("complete issuance flow — all four response fields are populated and internally consistent")
	void issueReturnsFullyPopulatedResponse () {
		BoletoIssuanceResponse response = issue(STANDARD);

		assertThat(response.providerChargeId()).isPositive();
		assertThat(response.barcode()).isNotBlank();
		assertThat(response.billetLink()).startsWith("https://");
		assertThat(response.pdfUrl()).startsWith("https://");

		// The billet and PDF are different resources; their URLs must differ.
		assertThat(response.billetLink()).isNotEqualTo(response.pdfUrl());
	}

	@Test
	@DisplayName("two independently issued boletos must receive distinct chargeIds")
	void twoIssuancesProduceDistinctChargeIds () {
		BoletoIssuanceResponse first = issue(STANDARD, CPF_1);
		BoletoIssuanceResponse second = issue(STANDARD, CPF_2);

		assertThat(first.providerChargeId()).isNotEqualTo(second.providerChargeId());
		assertThat(first.barcode()).isNotEqualTo(second.barcode());
	}

	@Test
	@DisplayName("minimum accepted amount (R$ 5.00) is accepted by the Efí Sandbox and returns a structurally valid response")
	void issueWithMinimumAmountIsAccepted () {
		BoletoIssuanceResponse response = issue(AMOUNT_BOLETO_MIN);

		assertThat(response.providerChargeId()).isPositive();

		String digitsOnly = response.barcode().replaceAll("[.\\s]", "");
		assertThat(digitsOnly)
			.matches("\\d+")
			.hasSizeGreaterThanOrEqualTo(FEBRABAN_STANDARD_BARCODE_NUMERIC_LENGTH);
	}

	@Test
	@DisplayName("issuing a boleto with amount exceeding Efí sandbox operational limit fails fast with a non-retryable gateway exception")
	void issueWithLargeAmountExceedsOperationalLimit () {
		// Efí returns 4600037 when the issuance amount exceeds the account's operational ceiling.
		// This is a permanent business rule rejection — retrying will never succeed.
		assertThatExceptionOfType(BoletoGatewayException.class)
			.isThrownBy(() -> issue(AMOUNT_LARGE))
			.withMessageContaining(EFI_ERROR_EXCEEDS_OPERATIONAL_LIMIT)
			.matches(TransientExceptionClassifier::isNonRetryable,
				"exception must be non-retryable: a limit breach is a permanent rejection, not a transient failure");
	}

	@Test
	@DisplayName("issuing a boleto for a CNPJ payer succeeds; the gateway routes juridical_person correctly")
	void issueForCnpjPayerSucceeds () {
		// The gateway distinguishes CPF/CNPJ by document length (14 chars = CNPJ).
		// Efí expects juridical_person.corporate_name + juridical_person.cnpj for CNPJ payers.
		BoletoIssuanceResponse response = issue(STANDARD, "Empresa Teste S/A", CNPJ_VALID);

		assertThat(response.providerChargeId()).isPositive();
		assertThat(response.barcode()).isNotBlank();
	}

	@Test
	@DisplayName("three sequential issuances in the same context all return structurally valid, distinct responses")
	void sequentialIssuancesAllReturnValidAndDistinctResponses () {
		List<BoletoIssuanceResponse> responses = new ArrayList<>();

		for (int i = 0; i < 3; i++) {
			BoletoIssuanceResponse response = issue(STANDARD, "Payer " + i, CPF_1);
			responses.add(response);

			assertThat(response.providerChargeId()).isPositive();
			assertThat(response.barcode()).isNotBlank();
			assertThat(response.billetLink()).startsWith("https://");
		}

		assertThat(responses)
			.extracting(BoletoIssuanceResponse::providerChargeId)
			.doesNotHaveDuplicates();

		assertThat(responses)
			.extracting(BoletoIssuanceResponse::barcode)
			.doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("issuing a boleto must complete without throwing when the sandbox is healthy — baseline smoke test")
	void issueDoesNotThrowUnderNormalConditions () {
		assertThatCode(() -> issue(STANDARD))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("polling a freshly issued boleto must return 'waiting' — the only valid status after a one-step issuance")
	void getChargeStatusReturnsWaitingForFreshlyIssuedCharge () {
		BoletoIssuanceResponse issued = issue(STANDARD);

		String status = efiBoletoGateway.getChargeStatus(issued.providerChargeId());

		assertThat(status)
			.isNotBlank()
			.isEqualTo(EFI_BOLETO_ONE_STEP_INITIAL_STATUS_WAITING);
	}

	@Test
	@DisplayName("polling the same chargeId twice must return the same status — status is stable between back-to-back reads")
	void getChargeStatusIsStableAcrossConsecutiveCalls () {
		BoletoIssuanceResponse issued = issue(STANDARD);

		String first = efiBoletoGateway.getChargeStatus(issued.providerChargeId());
		String second = efiBoletoGateway.getChargeStatus(issued.providerChargeId());

		assertThat(second)
			.isEqualTo(first)
			.isEqualTo(EFI_BOLETO_ONE_STEP_INITIAL_STATUS_WAITING);
	}

	@Test
	@DisplayName("polling a non-existent chargeId must throw BoletoGatewayException — the gateway must not swallow the provider 404")
	void getChargeStatusThrowsForNonExistentChargeId () {
		// Polling an unknown ID indicates a persistence bug in our system — surfacing it via
		// an exception is intentional, as opposed to returning an empty Optional.
		long nonExistentId = Long.MAX_VALUE;

		assertThatThrownBy(() -> efiBoletoGateway.getChargeStatus(nonExistentId))
			.isInstanceOf(BoletoGatewayException.class)
			.hasMessageContaining("404", "not found", "getChargeStatus");
	}

	@Test
	@DisplayName("Sandbox must enforce authentication by returning 401 when an invalid bearer token is provided")
	void issueBoletoRawHttpSandboxReturns401WhenBearerTokenIsInvalid () {
		unauthenticatedBearerBoletoSandboxClient()
			.post()
			.uri("/v1/charge/one-step")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(efiBoletoJson(STANDARD, CPF_1))
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("getAccessToken must return a valid opaque non-blank string with no whitespace on the first call")
	void getAccessTokenReturnsValidOpaqueTokenOnFirstCall () {
		String token = efiBoletoAuthGateway.getAccessToken();

		assertThat(token)
			.isNotBlank()
			.doesNotContainAnyWhitespaces()
			.hasSizeGreaterThan(20);
	}

	@Test
	@DisplayName("subsequent calls within the TTL window must return the cached token without a new network round-trip")
	void getAccessTokenReturnsSameTokenOnSubsequentCallsWithinTtl () {
		String first = efiBoletoAuthGateway.getAccessToken();
		String second = efiBoletoAuthGateway.getAccessToken();

		assertThat(second).isEqualTo(first).isNotBlank();
	}

	@Test
	@DisplayName("a stale cached token is evicted and issuance transparently recovers after a simulated 401 from Efí Bank")
	void issueBoletoEvictsStaleCachedTokenAndRecoversTransparentlyAfter401 () {
		Cache tokenCache = requireBoletoTokenCache();
		tokenCache.put("access_token", "deliberately-invalid-token");

		BoletoIssuanceResponse response = issue(STANDARD);

		assertThat(response.providerChargeId()).isPositive();
		assertThat(response.barcode()).isNotBlank();
		assertThat(response.billetLink()).startsWith("https://");

		Cache.ValueWrapper refreshedEntry = tokenCache.get("access_token");
		assertThat(refreshedEntry).isNotNull();
		assertThat((String) refreshedEntry.get())
			.isNotBlank()
			.isNotEqualTo("deliberately-invalid-token")
			.doesNotContainAnyWhitespaces()
			.hasSizeGreaterThan(20);
	}

	@Test
	@DisplayName("cache contains the refreshed token after 401 recovery — subsequent calls use the new token")
	void tokenCacheContainsRefreshedTokenAfterRecovery () {
		Cache tokenCache = requireBoletoTokenCache();
		tokenCache.put("access_token", "stale-token-xyz");

		issue(STANDARD);

		Cache.ValueWrapper cachedEntry = tokenCache.get("access_token");
		assertThat(cachedEntry).isNotNull();

		String newToken = (String) cachedEntry.get();
		assertThat(newToken)
			.isNotBlank()
			.isNotEqualTo("stale-token-xyz")
			.doesNotContainAnyWhitespaces()
			.hasSizeGreaterThan(20);
	}

	@AfterEach
	void logIssuedChargesForManualCleanup () {
		// Efí Cobranças sandbox has no DELETE/cancel endpoint for boletos via the standard scope,
		// so we log issued IDs to assist manual cleanup rather than attempting cancellation.
		if (!issuedChargeIds.isEmpty()) {
			System.out.println("Post-test issued boleto chargeIds (sandbox cleanup may be needed): " + issuedChargeIds);
		}
		issuedChargeIds.clear();
	}

	private BoletoIssuanceResponse issue (BigDecimal amount) {
		return issue(amount, "E2E Payer", CPF_1);
	}

	private BoletoIssuanceResponse issue (BigDecimal amount, String payerDocument) {
		return issue(amount, "E2E Payer", payerDocument);
	}

	private BoletoIssuanceResponse issue (BigDecimal amount, String payerName, String payerDocument) {
		var request = new BoletoIssuanceRequest(
			payerName,
			payerDocument,
			BOLETO_PAYER_ADDRESS,
			amount,
			DUE_DATE,
			DESCRIPTION,
			boletoProperties.notificationUrl()
		);

		BoletoIssuanceResponse response = efiBoletoGateway.issue(request);
		issuedChargeIds.add(response.providerChargeId());
		return response;
	}

	private Cache requireBoletoTokenCache () {
		Cache cache = cacheManager.getCache("efi-cobrancas-oauth-token");
		assertThat(cache)
			.as("Cache 'efi-cobrancas-oauth-token' must be configured in CacheManager")
			.isNotNull();
		return cache;
	}

	/**
	 * Raw Efí JSON body for direct sandbox HTTP assertions.
	 * Must match Efí's one-step schema exactly (not our internal DTOs) because these tests
	 * exist precisely to catch drift between the two.
	 */
	private String efiBoletoJson (BigDecimal amount, String cpf) {
		int amountInCents = amount
			.multiply(BigDecimal.valueOf(100))
			.setScale(0, RoundingMode.HALF_UP)
			.intValueExact();

		return """
			{
			  "items": [
			    {
			      "name": "Meu Produto",
			      "value": %d,
			      "amount": 1
			    }
			  ],
			  "payment": {
			    "banking_billet": {
			      "customer": {
			        "name": "Gorbadoc Oldbuck",
			        "cpf": "%s",
			        "email": "email_do_cliente@servidor.com.br",
			        "phone_number": "5144916523",
			        "address": {
			          "street": "Avenida Juscelino Kubitschek",
			          "number": "909",
			          "neighborhood": "Bauxita",
			          "zipcode": "35400000",
			          "city": "Ouro Preto",
			          "complement": "",
			          "state": "MG"
			        }
			      },
			      "expire_at": "%s",
			      "configurations": {
			        "fine": 200,
			        "interest": 33
			      },
			      "message": "Boleto E2E test."
			    }
			  }
			}
			""".formatted(amountInCents, cpf, DUE_DATE);
	}
}
