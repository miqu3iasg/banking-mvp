package com.miqu3iasg.banking_mvp.auth.token;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking_mvp.auth.support.AbstractAuthIntegrationTest;
import com.miqu3iasg.banking_mvp.auth.support.AuthTestDataFactory;
import com.miqu3iasg.banking_mvp.auth.support.JwtTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

@DisplayName("Token Lifecycle Tests")
class TokenLifecycleIntegrationTest extends AbstractAuthIntegrationTest {

	@Autowired
	private AuthTestDataFactory factory;

	@Autowired
	private JwtTestHelper jwtHelper;

	@Test
	void should_return200_when_validRefreshTokenAndRotation () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		EntityExchangeResult<byte[]> loginResponse = webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.returnResult();

		String refreshToken = extractRefreshToken(loginResponse.getResponseBody());

		webTestClient.post().uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"refreshToken":"%s"}
				""".formatted(refreshToken))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.accessToken").exists()
			.jsonPath("$.refreshToken").exists();
	}

	@Test
	void should_return401_when_revokedRefreshTokenReused () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		EntityExchangeResult<byte[]> loginResponse = webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.returnResult();

		String refreshToken = extractRefreshToken(loginResponse.getResponseBody());

		webTestClient.post().uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"refreshToken":"%s"}
				""".formatted(refreshToken))
			.exchange()
			.expectStatus().isOk();

		webTestClient.post().uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"refreshToken":"%s"}
				""".formatted(refreshToken))
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	void should_return401_when_expiredRefreshToken () throws Exception {
		webTestClient.post().uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"refreshToken":"nonexistent-token-value"}
				""")
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	void should_return401_when_tamperedRefreshToken () throws Exception {
		webTestClient.post().uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"refreshToken":"tampered-token-with-invalid-format-!!!@@@"}
				""")
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	void should_return400_when_blankRefreshToken () throws Exception {
		webTestClient.post().uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"refreshToken":""}
				""")
			.exchange()
			.expectStatus().isEqualTo(422);
	}

	private String extractRefreshToken (byte[] content) throws Exception {
		com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
		return json.get("refreshToken").asText();
	}
}
