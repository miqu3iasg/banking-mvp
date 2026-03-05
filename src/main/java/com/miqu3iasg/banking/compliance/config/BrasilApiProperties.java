package com.miqu3iasg.banking.compliance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brasil-api")
public record BrasilApiProperties(
	String baseUrl,
	int connectTimeoutSeconds,
	int readTimeoutSeconds
) {
	public BrasilApiProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://brasilapi.com.br/api";
		}

		if (connectTimeoutSeconds <= 0) connectTimeoutSeconds = 3;
		if (readTimeoutSeconds <= 0) readTimeoutSeconds = 5;
	}
}
