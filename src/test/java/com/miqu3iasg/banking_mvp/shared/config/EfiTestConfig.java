package com.miqu3iasg.banking_mvp.shared.config;

import com.miqu3iasg.banking.boleto.config.EfiBoletoProperties;
import com.miqu3iasg.banking.boleto.gateway.EfiBoletoAuthGateway;
import com.miqu3iasg.banking.boleto.gateway.EfiBoletoGateway;
import com.miqu3iasg.banking.boleto.metrics.BoletoMetrics;
import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import com.miqu3iasg.banking.pix.gateway.EfiEvpGateway;
import com.miqu3iasg.banking.pix.gateway.EfiPixAuthGateway;
import com.miqu3iasg.banking.pix.gateway.EfiPixGateway;
import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
import com.miqu3iasg.banking.pix.service.WebhookRegistrationService;
import com.miqu3iasg.banking.shared.config.RetryProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class EfiTestConfig {

	@Bean
	@Primary
	public EfiBoletoAuthGateway efiBoletoAuthGateway (EfiBoletoProperties props) {
		return new EfiBoletoAuthGateway(WebClient.create(), props);
	}

	@Bean
	@Primary
	public EfiPixAuthGateway efiPixAuthGateway (EfiPixProperties props) {
		return new EfiPixAuthGateway(WebClient.create(), props);
	}

	@Bean
	@Primary
	public EfiBoletoGateway efiBoletoGateway (
		EfiBoletoAuthGateway efiBoletoAuthGateway,
		EfiBoletoProperties props,
		BoletoMetrics metrics,
		CacheManager cacheManager
	) {
		return new EfiBoletoGateway(WebClient.create(), efiBoletoAuthGateway, props, metrics, cacheManager);
	}

	@Bean
	@Primary
	public EfiPixGateway efiPixGateway (
		EfiPixAuthGateway efiPixAuthGateway,
		PixMetrics metrics,
		CacheManager cacheManager,
		RetryProperties retryProperties,
		@Qualifier("efiRetryTemplate") RetryTemplate retryTemplate
	) {
		return new EfiPixGateway(WebClient.create(), efiPixAuthGateway, metrics, cacheManager, retryProperties, retryTemplate);
	}

	@Bean
	@Primary
	public EfiEvpGateway efiEvpGateway (EfiPixAuthGateway efiPixAuthGateway, PixMetrics metrics) {
		return new EfiEvpGateway(WebClient.create(), efiPixAuthGateway, metrics);
	}

	@Bean
	@Primary
	public WebhookRegistrationService webhookRegistrationService (
		PixKeyRepository pixKeyRepository,
		EfiPixAuthGateway efiPixAuthGateway,
		EfiPixProperties props
	) {
		return new WebhookRegistrationService(pixKeyRepository, efiPixAuthGateway, props, WebClient.create());
	}
}
