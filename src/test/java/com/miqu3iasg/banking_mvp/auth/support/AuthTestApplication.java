package com.miqu3iasg.banking_mvp.auth.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(
    scanBasePackages = {"com.miqu3iasg.banking", "com.miqu3iasg.banking_mvp"}
)
@ComponentScan(
    basePackages = {"com.miqu3iasg.banking", "com.miqu3iasg.banking_mvp"},
    excludeFilters = {
        @Filter(type = FilterType.REGEX, pattern = "com\\.miqu3iasg\\.banking\\.boleto\\..*"),
        @Filter(type = FilterType.REGEX, pattern = "com\\.miqu3iasg\\.banking\\.pix\\..*"),
        @Filter(type = FilterType.REGEX, pattern = "com\\.miqu3iasg\\.banking\\.shared\\.config\\.EfiWebClientConfig"),
        @Filter(type = FilterType.REGEX, pattern = "com\\.miqu3iasg\\.banking\\.shared\\.config\\.WebhookMtlsConfig"),
        @Filter(type = FilterType.REGEX, pattern = "com\\.miqu3iasg\\.banking\\.shared\\.audit\\..*")
    }
)
public class AuthTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthTestApplication.class, args);
    }
}
