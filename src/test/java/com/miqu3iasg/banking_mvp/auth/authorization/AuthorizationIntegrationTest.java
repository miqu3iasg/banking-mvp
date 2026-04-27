package com.miqu3iasg.banking_mvp.auth.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking_mvp.auth.support.AbstractAuthIntegrationTest;
import com.miqu3iasg.banking_mvp.auth.support.AuthTestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

class AuthorizationIntegrationTest extends AbstractAuthIntegrationTest {

	@Autowired
	private AuthTestDataFactory factory;

	@Test
	void unauthenticatedRequestToProtectedEndpointIsRejected () {
		webTestClient.get().uri("/api/v1/auth/me")
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	void activeUserWithValidTokenCanAccessOwnProfile () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		EntityExchangeResult<byte[]> loginResult = webTestClient
			.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{
					"email":"%s",
					"password":"TestP@ssw0rd123!"
				}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.returnResult();

		String accessToken = extractField(loginResult.getResponseBody(), "accessToken");

		webTestClient.get().uri("/api/v1/auth/me")
			.headers(h -> h.setBearerAuth(accessToken))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.email").isEqualTo(user.getEmail());
	}

	@Test
	void shouldReturn401WhenJwtIsExpired () throws Exception {
		webTestClient.get().uri("/api/v1/auth/me")
			.headers(h -> h.setBearerAuth("expired-token-value"))
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	void shouldReturn401WhenJwtIsTampered () throws Exception {
		webTestClient.get().uri("/api/v1/auth/me")
			.headers(h -> h.setBearerAuth("tampered.token.value"))
			.exchange()
			.expectStatus().isUnauthorized();
	}

	private String extractField (byte[] json, String field) throws Exception {
		JsonNode node = new ObjectMapper().readTree(json);

		return node.get(field).asText();
	}
}
