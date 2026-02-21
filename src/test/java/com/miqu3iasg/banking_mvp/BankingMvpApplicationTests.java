package com.miqu3iasg.banking_mvp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BankingMvpApplicationTests {

	@Test
	void contextLoads() {
	}

}
