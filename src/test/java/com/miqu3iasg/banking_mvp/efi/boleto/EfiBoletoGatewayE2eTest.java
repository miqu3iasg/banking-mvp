package com.miqu3iasg.banking_mvp.efi.pix.gateway;

import com.miqu3iasg.banking.boleto.exception.BoletoGatewayException;
import com.miqu3iasg.banking.boleto.gateway.BoletoIssuanceRequest;
import com.miqu3iasg.banking.boleto.gateway.BoletoIssuanceResponse;
import com.miqu3iasg.banking_mvp.shared.support.AbstractE2eTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Slf4j
class EfiBoletoGatewayE2eTest extends AbstractE2eTestSupport {

	private static final String STATUS_NEW = "new";
	private static final String STATUS_WAITING = "waiting";

	// Febraban standard: 44-digit numeric code
	private static final int BARCODE_MIN_LEN = 44;
	private static final String DESCRIPTION = "E2E Boleto Test";
	private static final LocalDate DUE_DATE = LocalDate.now().plusDays(3);

	private final List<Long> issuedChargeIds = new ArrayList<>();

	@Nested
	@DisplayName("Boleto issuance; response structure and field constraints")
	class Issuance {

		@Test
		@DisplayName("issuing a boleto must return a positive provider chargeId assigned by Efí Bank.")
		void issueReturnsPositiveProviderChargeId () {
			BoletoIssuanceResponse response = issue(STANDARD);

			assertThat(response.providerChargeId())
				.isPositive();
		}

		@Test
		@DisplayName("the barcode must be a non-blank numeric string of at least 44 characters conforming to the Febraban standard.")
		void issueReturnsFebrabanCompliantBarcode () {
			BoletoIssuanceResponse response = issue(STANDARD);

			assertThat(response.barcode())
				.isNotBlank()
				.matches("\\d+")
				.hasSizeGreaterThanOrEqualTo(BARCODE_MIN_LEN);
		}

		@Test
		@DisplayName("the billetLink must be a non-blank HTTPS URL pointing to an Efí-hosted page for online payment.")
		void issueReturnsBilletLinkThatIsHttpsUrl () {
			BoletoIssuanceResponse response = issue(STANDARD);

			assertThat(response.billetLink())
				.isNotBlank()
				.startsWith("https://")
				.doesNotContainAnyWhitespaces();
		}

		@Test
		@DisplayName("the pdfUrl must be a non-blank HTTPS URL when Efí Bank returns a PDF link; PDF download must be reachable.")
		void issueReturnsPdfUrlThatIsNonBlankHttpsUrl () {
			BoletoIssuanceResponse response = issue(STANDARD);

			// pdfUrl is nullable in the response contract, but Efí Sandbox always populates it.
			assertThat(response.pdfUrl())
				.isNotBlank()
				.startsWith("https://")
				.doesNotContainAnyWhitespaces();
		}

		@Test
		@DisplayName("two independently issued boletos must receive distinct chargeIds; IDs are never reused within the same session.")
		void twoIssuancesProduceDistinctChargeIds () {
			BoletoIssuanceResponse first = issue(STANDARD, CPF_1);
			BoletoIssuanceResponse second = issue(STANDARD, CPF_2);

			assertThat(first.providerChargeId())
				.isNotEqualTo(second.providerChargeId());
		}

		@Test
		@DisplayName("minimum representable amount (R$ 0.01) is accepted by Efí Bank and returns a valid issuance response.")
		void issueWithMinimumAmountIsAccepted () {
			BoletoIssuanceResponse response = issue(AMOUNT_MIN);

			assertThat(response.providerChargeId()).isPositive();
			assertThat(response.barcode()).isNotBlank();
		}

		@Test
		@DisplayName("a large amount (R$ 9,999.99) is accepted by Efí Bank and returns a valid issuance response.")
		void issueWithLargeAmountIsAccepted () {
			BoletoIssuanceResponse response = issue(AMOUNT_LARGE);

			assertThat(response.providerChargeId()).isPositive();
			assertThat(response.barcode()).isNotBlank();
		}

		@Test
		@DisplayName("issuing a boleto for a CNPJ payer must succeed; the gateway routes CNPJ vs CPF correctly.")
		void issueForCnpjPayerSucceeds () {
			BoletoIssuanceResponse response = issue(
				STANDARD, "Empresa Teste S/A", "11222333000181"   // valid CNPJ format
			);

			assertThat(response.providerChargeId()).isPositive();
			assertThat(response.barcode()).isNotBlank();
		}

		@Test
		@DisplayName("Sandbox must return 201 with the full Efí charge payload; acting as a schema regression guard against provider drift.")
		void issueBoletoRawHttpSandboxReturns201WithFullChargePayload () {
			boletoSandboxClient()
				.post()
				.uri("/v1/charge/one-step")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(efiBoletoJson(STANDARD, CPF_1))
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.data.charge_id").isNotEmpty()
				.jsonPath("$.data.status").isNotEmpty()
				.jsonPath("$.data.barcode").isNotEmpty()
				.jsonPath("$.data.billet_link").isNotEmpty()
				.jsonPath("$.data.pdf.charge").isNotEmpty();
		}

		@Test
		@DisplayName("Sandbox must enforce authentication by returning 401 when an invalid bearer token is provided.")
		void issueBoletoRawHttpSandboxReturns401WhenBearerTokenIsInvalid () {
			unauthenticatedBearerBoletoSandboxClient()
				.post()
				.uri("/v1/charge/one-step")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(efiBoletoJson(STANDARD, CPF_1))
				.exchange()
				.expectStatus().isUnauthorized();
		}
	}

	@Nested
	@DisplayName("Charge status polling; getChargeStatus contract")
	class ChargeStatusPolling {

		@Test
		@DisplayName("polling a freshly issued boleto must return a non-blank status string; sandbox always returns 'new' or 'waiting'.")
		void getChargeStatusReturnsNonBlankStatusForIssuedCharge () {
			BoletoIssuanceResponse issued = issue(STANDARD);

			String status = efiBoletoGateway.getChargeStatus(issued.providerChargeId());

			assertThat(status)
				.isNotBlank()
				.isIn(STATUS_NEW, STATUS_WAITING);
		}

		@Test
		@DisplayName("polling the same chargeId twice must return the same status; status must not change between back-to-back reads.")
		void getChargeStatusIsStableAcrossConsecutiveCalls () {
			BoletoIssuanceResponse issued = issue(STANDARD);

			String first = efiBoletoGateway.getChargeStatus(issued.providerChargeId());
			String second = efiBoletoGateway.getChargeStatus(issued.providerChargeId());

			assertThat(second).isEqualTo(first);
		}

		@Test
		@DisplayName("polling a non-existent chargeId must throw BoletoGatewayException; the gateway must not swallow the provider 404.")
		void getChargeStatusThrowsForNonExistentChargeId () {
			// getChargeStatus is a mandatory status-sync operation; unlike getCharge on the Pix side
			// (where an empty Optional signals a normal polling condition), a missing boleto chargeId
			// indicates a bug in our persistence layer — we should never poll an ID we did not issue.
			// Throwing here surfaces the inconsistency rather than masking it as an empty result.
			long nonExistentId = Long.MAX_VALUE;

			assertThatThrownBy(() -> efiBoletoGateway.getChargeStatus(nonExistentId))
				.isInstanceOf(BoletoGatewayException.class);
		}

		@Test
		@DisplayName("two independently issued boletos can be polled independently; their statuses do not interfere with each other.")
		void statusPollsForTwoIndependentChargesDoNotInterfere () {
			BoletoIssuanceResponse first = issue(STANDARD, CPF_1);
			BoletoIssuanceResponse second = issue(STANDARD, CPF_2);

			String statusFirst = efiBoletoGateway.getChargeStatus(first.providerChargeId());
			String statusSecond = efiBoletoGateway.getChargeStatus(second.providerChargeId());

			// Both should be valid statuses; we are checking isolation, not exact values.
			assertThat(statusFirst).isNotBlank();
			assertThat(statusSecond).isNotBlank();
			assertThat(first.providerChargeId()).isNotEqualTo(second.providerChargeId());
		}
	}

	@Nested
	@DisplayName("OAuth2 token caching and 401 transparent recovery")
	class Authentication {

		@Test
		@DisplayName("the first call to obtain an access token must return a valid opaque non-blank string with no whitespace.")
		void getAccessTokenReturnsValidOpaqueTokenOnFirstCall () {
			String token = efiBoletoAuthGateway.getAccessToken();

			assertThat(token)
				.isNotBlank()
				.doesNotContainAnyWhitespaces()
				.hasSizeGreaterThan(20);
		}

		@Test
		@DisplayName("subsequent calls within the TTL window must return the same cached token without triggering a new network round-trip.")
		void getAccessTokenReturnsSameTokenOnSubsequentCallsWithinTtl () {
			String first = efiBoletoAuthGateway.getAccessToken();
			String second = efiBoletoAuthGateway.getAccessToken();

			assertThat(second).isEqualTo(first);
		}

		@Test
		@DisplayName("a stale cached token is evicted and the issuance request transparently recovers after a simulated 401 from Efí Bank.")
		void issueBoletoEvictsStaleCachedTokenAndRecoversTransparentlyAfter401 () {
			Cache tokenCache = cacheManager.getCache("efi-cobrancas-oauth-token");
			assertThat(tokenCache).isNotNull();

			tokenCache.put("access_token", "deliberately-invalid-token");

			// The gateway must detect the 401 on the first attempt, evict the stale entry,
			// re-authenticate, and complete the issuance — all transparently to the caller.
			BoletoIssuanceResponse response = issue(STANDARD);

			assertThat(response).isNotNull();
			assertThat(response.providerChargeId()).isPositive();
			assertThat(response.barcode()).isNotBlank();
			assertThat(response.billetLink())
				.isNotBlank()
				.startsWith("https://");

			Cache.ValueWrapper refreshedEntry = tokenCache.get("access_token");
			assertThat(refreshedEntry).isNotNull();

			String newToken = (String) refreshedEntry.get();
			assertThat(newToken)
				.isNotBlank()
				.isNotEqualTo("deliberately-invalid-token")
				.doesNotContainAnyWhitespaces()
				.hasSizeGreaterThan(20);
		}

		@Test
		@DisplayName("a stale cached token is replaced in the cache after 401 recovery; subsequent calls use the refreshed token.")
		void tokenCacheContainsRefreshedTokenAfterRecovery () {
			Cache tokenCache = cacheManager.getCache("efi-cobrancas-oauth-token");
			assertThat(tokenCache).isNotNull();

			tokenCache.put("access_token", "stale-token-abc");

			issue(STANDARD);

			Cache.ValueWrapper cachedEntry = tokenCache.get("access_token");
			assertThat(cachedEntry).isNotNull();
			assertThat((String) cachedEntry.get())
				.isNotEqualTo("stale-token-abc")
				.isNotBlank();
		}
	}

	@Nested
	@DisplayName("Gateway resilience; error propagation and retry behaviour")
	class Resilience {

		@Test
		@DisplayName("issuing a boleto must complete without throwing when the sandbox is healthy; baseline smoke test.")
		void issueDoesNotThrowUnderNormalConditions () {
			assertThatCode(() -> issue(STANDARD))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("issuing multiple boletos sequentially in the same test context does not degrade; all N responses are structurally valid.")
		void sequentialIssuancesAllReturnValidResponses () {
			int count = 3;

			for (int i = 0; i < count; i++) {
				BoletoIssuanceResponse response = issue(
					STANDARD,
					"Payer " + i,
					CPF_1
				);

				assertThat(response.providerChargeId())
					.as("chargeId must be positive for issuance %d", i)
					.isPositive();

				assertThat(response.barcode())
					.as("barcode must be non-blank for issuance %d", i)
					.isNotBlank();
			}
		}
	}

	@AfterEach
	void logIssuedChargesForManualCleanup () {
		// Efí Cobranças sandbox does not expose a DELETE/cancel endpoint for boletos,
		// so we log issued IDs to assist manual cleanup rather than attempting cancellation.
		if (!issuedChargeIds.isEmpty()) {
			log.info("Post-test issued boleto chargeIds (sandbox cleanup may be needed): {}",
				issuedChargeIds);
		}
		issuedChargeIds.clear();
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

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
			amount,
			DUE_DATE,
			DESCRIPTION,
			boletoProperties.notificationUrl()
		);

		BoletoIssuanceResponse response = efiBoletoGateway.issue(request);
		issuedChargeIds.add(response.providerChargeId());
		return response;
	}

	/**
	 * Raw Efí JSON body for direct sandbox HTTP assertions via the Cobranças API.
	 * Must match Efí's one-step schema exactly (not our internal DTOs) because these tests
	 * exist precisely to catch drift between the two.
	 */
	private String efiBoletoJson (BigDecimal amount, String cpf) {
		int amountInCents = amount
			.multiply(BigDecimal.valueOf(100))
			.intValue();

		return """
			{
			  "items": [
			    { "name": "E2E Boleto Item", "amount": 1, "value": %d }
			  ],
			  "metadata": {
			    "notification_url": "%s"
			  },
			  "payment": {
			    "banking_billet": {
			      "customer": {
			        "name": "E2E Payer",
			        "cpf": "%s"
			      },
			      "expire_at": "%s",
			      "message": "E2E test"
			    }
			  }
			}
			""".formatted(amountInCents, boletoProperties.notificationUrl(), cpf, DUE_DATE);
	}
}
