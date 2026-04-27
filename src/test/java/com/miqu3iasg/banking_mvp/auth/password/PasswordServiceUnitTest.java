package com.miqu3iasg.banking_mvp.auth.password;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.service.PasswordService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Password Service Unit Tests")
class PasswordServiceUnitTest {

	private PasswordService passwordService;

	@BeforeEach
	void setUp() {
		AuthProperties authProperties = new AuthProperties();
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		passwordService = new PasswordService(authProperties, meterRegistry);
		passwordService.init();
	}

	@Test
	void should_rejectCommonPasswords () {
		assertThat(passwordService.isCommonPassword("password123")).isTrue();
		assertThat(passwordService.isCommonPassword("123456")).isTrue();
		assertThat(passwordService.isCommonPassword("qwerty")).isTrue();
		assertThat(passwordService.isCommonPassword("MyP@ssword123!")).isFalse();
	}

	@Test
	void should_encodeAndMatchPassword () {
		String raw = "SecureP@ssw0rd123!";
		String encoded = passwordService.encode(raw);

		assertThat(passwordService.matches(raw, encoded)).isTrue();
		assertThat(passwordService.matches("WrongP@ssw0rd123!", encoded)).isFalse();
	}

	@Test
	void should_returnFalseForNullInputs () {
		assertThat(passwordService.matches(null, "encoded")).isFalse();
		assertThat(passwordService.matches("raw", null)).isFalse();
	}

	@Test
	void should_generateRandomPasswordOfCorrectLength () {
		String password = passwordService.generateRandomPassword(16);
		assertThat(password).hasSize(16);

		String password2 = passwordService.generateRandomPassword(16);
		assertThat(password2).hasSize(16);
		assertThat(password).isNotEqualTo(password2);
	}
}
