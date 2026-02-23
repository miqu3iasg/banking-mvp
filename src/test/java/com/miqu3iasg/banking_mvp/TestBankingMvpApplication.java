package com.miqu3iasg.banking_mvp;

import com.miqu3iasg.banking.BankingMvpApplication;
import org.springframework.boot.SpringApplication;

public class TestBankingMvpApplication {

	public static void main(String[] args) {
		SpringApplication.from(BankingMvpApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
