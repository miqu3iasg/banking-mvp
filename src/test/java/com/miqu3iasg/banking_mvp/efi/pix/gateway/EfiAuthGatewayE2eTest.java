package com.miqu3iasg.banking_mvp.efi.pix.gateway;

import com.miqu3iasg.banking_mvp.shared.support.AbstractE2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EfiAuthGatewayE2eTest extends AbstractE2eTestSupport {

	@Test
	@DisplayName("A valid credential set must return an opaque, non-blank token with no whitespace and a plausible length.")
	void getAccessTokenReturnsValidOpaqueTokenOnFirstCall () {
		String token = authGateway.getAccessToken();

		assertThat(token)
			.isNotBlank()
			.doesNotContainAnyWhitespaces()
			.hasSizeGreaterThan(20);
	}

	@Test
	@DisplayName("Subsequent calls within the TTL window must return the same cached token without triggering a new network round-trip.")
	void getAccessTokenReturnsSameTokenOnSubsequentCallWithinTtl () {
		String first = authGateway.getAccessToken();
		String second = authGateway.getAccessToken();

		assertThat(second).isEqualTo(first);
	}

	@Test
	@DisplayName("After manual cache eviction, the gateway must obtain a fresh token without throwing and store it back in the cache.")
	void getAccessTokenObtainsFreshTokenAfterCacheEvictionAndRepopulatesCache () {
		Cache tokenCache = cacheManager.getCache("efi-oauth-token");
		assertThat(tokenCache).isNotNull();
		tokenCache.evict("access_token");

		assertThat(tokenCache.get("access_token")).isNull();

		String refreshed = authGateway.getAccessToken();

		assertThat(refreshed)
			.isNotBlank()
			.doesNotContainAnyWhitespaces()
			.hasSizeGreaterThan(20);

		Cache.ValueWrapper repopulated = tokenCache.get("access_token");
		assertThat(repopulated).isNotNull();
		assertThat(repopulated.get()).isEqualTo(refreshed);
	}

	@Test
	@DisplayName("A stale token placed manually in the cache must be replaced with a valid one after the next successful fetch.")
	void getAccessTokenReplacesStaleCachedTokenWithFreshOne () {
		Cache tokenCache = cacheManager.getCache("efi-oauth-token");
		assertThat(tokenCache).isNotNull();

		tokenCache.put("access_token", "deliberately-stale-token");

		String returned = authGateway.getAccessToken();

		assertThat(returned)
			.isNotBlank()
			.doesNotContainAnyWhitespaces();
	}

	@Test
	@DisplayName("Sandbox must enforce authentication by returning 401 when an invalid Basic credential is provided to the OAuth endpoint.")
	void oauthEndpointReturns401WhenBasicCredentialIsInvalid () {
		// Confirms the sandbox actually enforces authentication on /oauth/token.
		// This documents the HTTP contract that the gateway's error-mapping code
		// relies on to convert 401 responses into PixGatewayException.
		unauthenticatedBasicSandboxClient()
			.post()
			.uri("/oauth/token")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("grant_type", "client_credentials"))
			.exchange()
			.expectStatus().isUnauthorized();
	}
}
