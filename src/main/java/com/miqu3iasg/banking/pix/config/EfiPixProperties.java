package com.miqu3iasg.banking.pix.config;

import com.miqu3iasg.banking.shared.config.EfiProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "efi.pix")
public record EfiPixProperties(

	@NotBlank String clientId,
	@NotBlank String clientSecret,

	/*
	  Filesystem path to the PKCS#12 (.p12) certificate downloaded from Efí Bank dashboard.
	  Mandatory on every request to the Efí PIX API, including the OAuth2 token request.
	  In ECS: mounted from AWS Secrets Manager as a file via ECS secrets.
	  Sandbox certs typically have an empty password.
	 */
	@NotBlank String certificatePath,

	/*
	  Password for the PKCS#12 certificate.
	  Efí Bank generates sandbox certificates with empty password by default.
	  Set certificatePassword: "" if the cert has no password.
	 */
	String certificatePassword,

	/*
	  Public HTTPS URL of our webhook endpoint, registered with Efí Bank per PIX key.

	  Format (from official docs): must end with "?ignorar=" to handle the "/pix" suffix
	  that Efí Bank appends to the URL on actual payment notifications.

	  Example: "https://api.our-domain.com/v1/pix/webhook?ignorar="

	  Efí sends a test probe to this URL during registration (PUT /v2/webhook/:chave).
	  The URL must be publicly reachable when registering — use ngrok for local dev.

	  See WebhookRegistrationService for the registration logic.
	 */
	@NotBlank String webhookUrl,

	/*
	  true  → Sandbox (https://pix-h.api.efipay.com.br)
	  false → Production (https://pix.api.efipay.com.br)

	  Defaults to true in application.yml so a misconfigured deployment
	  hits sandbox instead of billing real accounts.
	 */
	@NotNull Boolean sandbox,

	/*
	  QR Code validity window in seconds. Default: 3600 (1 hour).
	  Sandbox: charges ≤ R$10.00 auto-confirm within this window with webhook.
	  Charges > R$10.00 stay ATIVA without webhook in sandbox.
	 */
	@Positive int chargeExpiresInSeconds,

	/*
	  WebClient response timeout in seconds. Default: 30.
	  Controls how long the PIX WebClient waits for a response from Efí Bank
	  before raising a timeout error.
	 */
	@Positive int responseTimeoutInSeconds

) implements EfiProperties {

	/**
	 * Resolves the correct base URL based on the sandbox flag.
	 * Used by EfiWebClientConfig to configure the WebClient base URL.
	 * <p>
	 * Sandbox:    https://pix-h.api.efipay.com.br
	 * Production: https://pix.api.efipay.com.br
	 */
	@Override
	public String baseUrl () {
		return Boolean.TRUE.equals(sandbox)
			? "https://pix-h.api.efipay.com.br"
			: "https://pix.api.efipay.com.br";
	}
}
