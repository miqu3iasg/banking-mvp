package com.miqu3iasg.banking_mvp.auth.login;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking_mvp.auth.support.AbstractAuthIntegrationTest;
import com.miqu3iasg.banking_mvp.auth.support.AuthTestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Login & Authentication Tests")
class LoginIntegrationTest extends AbstractAuthIntegrationTest {

	@Autowired
	private AuthTestDataFactory factory;

	@Test
	void should_return200_when_validCredentialsAndMfaDisabled () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		webTestClient.post().uri("/api/v1/auth/login")
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
			.jsonPath("$.accessToken").exists()
			.jsonPath("$.refreshToken").exists()
			.jsonPath("$.tokenType").isEqualTo("Bearer")
			.jsonPath("$.expiresIn").isEqualTo(900)
			.jsonPath("$.roles").isArray()
			.jsonPath("$.requiresMfa").isEqualTo(false);
	}

	@Test
	void should_return401_when_passwordIsWrong () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"WrongP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isUnauthorized()
			.expectBody()
			.jsonPath("$.code").isEqualTo("AUTH_002");
	}

	@Test
	void should_return401_when_unknownEmail () throws Exception {
		long wrongPasswordStart = System.currentTimeMillis();
		User user = factory.createUser(AccountStatus.ACTIVE);
		webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"WrongP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isUnauthorized();
		long wrongPasswordTime = System.currentTimeMillis() - wrongPasswordStart;

		long unknownEmailStart = System.currentTimeMillis();
		webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"nonexistent-%s@test.com","password":"TestP@ssw0rd123!"}
				""".formatted(System.currentTimeMillis()))
			.exchange()
			.expectStatus().isUnauthorized();
		long unknownEmailTime = System.currentTimeMillis() - unknownEmailStart;

		assertThat(Math.abs(wrongPasswordTime - unknownEmailTime))
			.as("Response time difference should be small to prevent timing-based user enumeration")
			.isLessThan(500);
	}

	@Test
	void should_return403_when_accountPendingVerification () throws Exception {
		User user = factory.createUser(AccountStatus.PENDING_VERIFICATION);

		webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isForbidden()
			.expectBody()
			.jsonPath("$.code").isEqualTo("AUTH_005");
	}

	@Test
	void should_return423_when_accountIsLocked () throws Exception {
		User user = factory.createUser(AccountStatus.ACTIVE);

		for (int i = 0; i < 5; i++) {
			webTestClient.post().uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
					{"email":"%s","password":"WrongP@ssw0rd123!"}
					""".formatted(user.getEmail()))
				.exchange()
				.expectStatus().isUnauthorized();
		}

		webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isEqualTo(423)
			.expectBody()
			.jsonPath("$.code").isEqualTo("AUTH_003");
	}

	@Test
	void should_return403_when_accountSuspended () throws Exception {
		User user = factory.createUser(AccountStatus.SUSPENDED);

		webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!"}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isForbidden()
			.expectBody()
			.jsonPath("$.code").isEqualTo("AUTH_004");
	}

	@Test
	void should_return400_when_blankEmail () throws Exception {
		webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"","password":"TestP@ssw0rd123!"}
				""")
			.exchange()
			.expectStatus().isEqualTo(422);
	}

	@Test
	void should_return400_when_blankPassword () throws Exception {
		webTestClient.post().uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"test@test.com","password":""}
				""")
			.exchange()
			.expectStatus().isEqualTo(422);
	}
}
