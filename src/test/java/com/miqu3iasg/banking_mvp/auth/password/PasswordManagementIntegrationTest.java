package com.miqu3iasg.banking_mvp.auth.password;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking_mvp.auth.support.AbstractAuthIntegrationTest;
import com.miqu3iasg.banking_mvp.auth.support.AuthTestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

@DisplayName("Password Management Tests")
class PasswordManagementIntegrationTest extends AbstractAuthIntegrationTest {

	@Autowired
	private AuthTestDataFactory factory;

	@Test
	void should_return200_when_forgotPasswordWithRegisteredEmail () throws Exception {
		factory.createUser(AccountStatus.ACTIVE);

		webTestClient.post().uri("/api/v1/auth/password/reset")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s"}
				""".formatted("test@example.com"))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.message").isEqualTo("If an account with that email exists, a password reset link has been sent");
	}

	@Test
	void should_return200_when_forgotPasswordWithUnregisteredEmail () throws Exception {
		webTestClient.post().uri("/api/v1/auth/password/reset")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"nonexistent-%s@test.com"}
				""".formatted(System.currentTimeMillis()))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.message").isEqualTo("If an account with that email exists, a password reset link has been sent");
	}

	@Test
	void should_return200_when_changePasswordWithCorrectCurrentPassword () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		EntityExchangeResult<byte[]> loginResult = webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.returnResult();

		String accessToken = extractField(loginResult.getResponseBody(), "accessToken");

		webTestClient.post().uri("/api/v1/auth/password/change")
			.headers(h -> h.setBearerAuth(accessToken))
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"currentPassword":"TestP@ssw0rd123!","newPassword":"NewP@ssw0rd456!"}
				""")
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.message").isEqualTo("Password changed successfully");
	}

	@Test
	void should_return400_when_changePasswordWithWrongCurrentPassword () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		EntityExchangeResult<byte[]> loginResult = webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.returnResult();

		String accessToken = extractField(loginResult.getResponseBody(), "accessToken");

		webTestClient.post().uri("/api/v1/auth/password/change")
			.headers(h -> h.setBearerAuth(accessToken))
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"currentPassword":"WrongP@ssw0rd123!","newPassword":"NewP@ssw0rd456!"}
				""")
			.exchange()
			.expectStatus().isBadRequest();
	}

	@Test
	void should_return400_when_changePasswordWithBlankNewPassword () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		EntityExchangeResult<byte[]> loginResult = webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.returnResult();

		String accessToken = extractField(loginResult.getResponseBody(), "accessToken");

		webTestClient.post().uri("/api/v1/auth/password/change")
			.headers(h -> h.setBearerAuth(accessToken))
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"currentPassword":"TestP@ssw0rd123!","newPassword":""}
				""")
			.exchange()
			.expectStatus().isEqualTo(422);
	}

	@Test
	void should_return400_when_passwordResetConfirmWithBlankToken () throws Exception {
		webTestClient.post().uri("/api/v1/auth/password/reset/confirm")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"token":"","newPassword":"NewP@ssw0rd456!"}
				""")
			.exchange()
			.expectStatus().isEqualTo(422);
	}

	private String extractField (byte[] json, String field) throws Exception {
		com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
		return node.get(field).asText();
	}
}
