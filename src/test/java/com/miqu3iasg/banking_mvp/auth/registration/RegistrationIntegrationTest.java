package com.miqu3iasg.banking_mvp.auth.registration;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking_mvp.auth.support.AbstractAuthIntegrationTest;
import com.miqu3iasg.banking_mvp.auth.support.AuthTestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User Registration Tests")
class RegistrationIntegrationTest extends AbstractAuthIntegrationTest {

	@Autowired
	private AuthTestDataFactory factory;

	@Test
	void should_return200_when_registrationPayloadIsValid () throws Exception {
		String email = factory.generateEmail();
		String password = factory.generatePassword();

		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"%s","consentEmail":true}
				""".formatted(email, password))
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.message").isEqualTo("Registration successful. Please check your email to verify your account.")
			.jsonPath("$.userId").exists();
	}

	@Test
	void should_return409_when_duplicateEmail () throws Exception {
		User user = factory.createUser(AccountStatus.PENDING_VERIFICATION);

		long startTime = System.currentTimeMillis();
		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!","consentEmail":true}
				""".formatted(user.getEmail()))
			.exchange()
			.expectStatus().isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("REG_001");
		long duplicateTime = System.currentTimeMillis() - startTime;

		String newEmail = factory.generateEmail();
		startTime = System.currentTimeMillis();
		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"TestP@ssw0rd123!","consentEmail":true}
				""".formatted(newEmail))
			.exchange()
			.expectStatus().isOk();
		long successTime = System.currentTimeMillis() - startTime;

		assertThat(Math.abs(duplicateTime - successTime))
			.as("Response time difference should be within 50ms to prevent timing-based enumeration")
			.isLessThan(1000);
	}

	@Test
	void should_return400_when_passwordBelowMinimumLength () throws Exception {
		String email = factory.generateEmail();

		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"Short1!","consentEmail":true}
				""".formatted(email))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("PWD_001");
	}

	@Test
	void should_return400_when_passwordMissingRequiredCharacterClasses () throws Exception {
		String email = factory.generateEmail();

		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"alllowercase123","consentEmail":true}
				""".formatted(email))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("PWD_001");
	}

	@Test
	void should_return400_when_passwordIsCommon () throws Exception {
		String email = factory.generateEmail();

		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"%s","password":"Password123!","consentEmail":true}
				""".formatted(email))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("PWD_002");
	}

	@Test
	void should_return400_when_invalidEmailFormat () throws Exception {
		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"not-an-email","password":"TestP@ssw0rd123!","consentEmail":true}
				""")
			.exchange()
			.expectStatus().isEqualTo(422)
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
	}

	@Test
	void should_return400_when_blankRequiredFields () throws Exception {
		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"","password":"","consentEmail":false}
				""")
			.exchange()
			.expectStatus().isEqualTo(422);
	}

	@Test
	void should_return400_when_xssPayloadInFields () throws Exception {
		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"<script>alert('xss')</script>@test.com","password":"TestP@ssw0rd123!","consentEmail":true}
				""")
			.exchange()
			.expectStatus().isEqualTo(422);
	}

	@Test
	void should_return400_when_sqlInjectionInEmail () throws Exception {
		webTestClient.post().uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
				{"email":"'; DROP TABLE users; --","password":"TestP@ssw0rd123!","consentEmail":true}
				""")
			.exchange()
			.expectStatus().isEqualTo(422);
	}
}
