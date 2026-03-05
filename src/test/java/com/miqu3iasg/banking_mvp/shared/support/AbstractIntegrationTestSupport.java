package com.miqu3iasg.banking_mvp.shared.support;

import com.miqu3iasg.banking.BankingMvpApplication;
import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.api.dto.CreateAccountRequest;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.domain.AccountType;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = BankingMvpApplication.class)
@ActiveProfiles("integration-test")
public abstract class AbstractIntegrationTestSupport {

	protected static final String CPF_1 = "529.982.247-25";
	protected static final String CPF_2 = "111.444.777-35";
	protected static final String CPF_3 = "222.333.444-05";

	@Autowired
	protected AccountService accountService;

	@Autowired
	protected AccountRepository accountRepository;

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
		.withDatabaseName("banking_test")
		.withUsername("test")
		.withPassword("test")
		.withReuse(true);

	@DynamicPropertySource
	static void configureDataSource (DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("outbox.processor.enabled", () -> "false");
	}

	@BeforeEach
	void cleanDatabase () {
		accountRepository.deleteAll();
	}

	protected static CreateAccountRequest checking (String document) {
		return new CreateAccountRequest(
			"Jhon Doe",
			document,
			AccountType.CHECKING,
			"holder@example.com"
		);
	}

	protected static CreateAccountRequest savings (String document) {
		return new CreateAccountRequest(
			"Jane Doe",
			document,
			AccountType.SAVINGS,
			"savings@example.com"
		);
	}

	protected AccountResponse openChecking (String document) {
		return accountService.openAccount(checking(document));
	}

	protected AccountResponse openAndBlock (String document) {
		AccountResponse account = openChecking(document);
		return accountService.applyStatusAction(account.id(), AccountAction.BLOCK_ACCOUNT_USAGE);
	}

	protected AccountResponse openAndClose (String document) {
		AccountResponse account = openChecking(document);
		return accountService.applyStatusAction(account.id(), AccountAction.CLOSE_ACCOUNT);
	}

	protected String generateCpf (int seed) {
		int base = (seed + 1) * 13;
		int[] digits = new int[11];
		for (int i = 8; i >= 0; i--) {
			digits[i] = base % 10;
			base /= 10;
		}

		int sum = 0;
		for (int i = 0; i < 9; i++) sum += digits[i] * (10 - i);
		int remainder = sum % 11;
		digits[9] = remainder < 2 ? 0 : 11 - remainder;

		sum = 0;
		for (int i = 0; i < 10; i++) sum += digits[i] * (11 - i);
		remainder = sum % 11;
		digits[10] = remainder < 2 ? 0 : 11 - remainder;

		return String.format("%d%d%d.%d%d%d.%d%d%d-%d%d",
			digits[0], digits[1], digits[2],
			digits[3], digits[4], digits[5],
			digits[6], digits[7], digits[8],
			digits[9], digits[10]);
	}
}
