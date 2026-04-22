package com.miqu3iasg.banking.shared.config;

import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// The mTLS enforcement happens at the infrastructure layer (ALB or Nginx)
@Slf4j
@Configuration
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EfiPixProperties.class)
public class WebhookMtlsConfig {

	/**
	 * Certificate chain URLs for Efí Bank webhook mTLS.
	 * These must be downloaded and configured at the infrastructure layer.
	 */
	public static final String CERT_SANDBOX_URL = "https://certificados.efipay.com.br/webhooks/certificate-chain-homolog.crt";
	public static final String CERT_PRODUCTION_URL = "https://certificados.efipay.com.br/webhooks/certificate-chain-prod.crt";

	/**
	 * Efí Bank's current IP used for webhook delivery from official docs.
	 * Used for IP allowlisting when skip-mTLS mode is active.
	 */
	public static final String EFI_WEBHOOK_IP = "34.193.116.226";
}
