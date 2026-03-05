package com.miqu3iasg.banking.boleto.config;

import com.miqu3iasg.banking.shared.config.EfiProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for Efí Bank's Cobranças API (boleto, cartão, etc.).
 * <p>
 * Implements {@link EfiProperties} so that {@code EfiWebClientConfig} can build
 * the {@code efiBoletoWebClient} bean using the same mTLS factory as the PIX client.
 * The certificate fields mirror those in {@link com.miqu3iasg.banking.pix.config.EfiPixProperties}
 * — Efí Bank issues one PKCS#12 certificate per account, but each environment profile
 * may reference the same or a different file path depending on deployment.
 * <p>
 * Sandbox base URL:    {@code https://cobrancas-h.api.efipay.com.br}
 * Production base URL: {@code https://cobrancas.api.efipay.com.br}
 */
@Validated
@ConfigurationProperties(prefix = "efi.boleto")
public record EfiBoletoProperties(

    @NotBlank String clientId,
    @NotBlank String clientSecret,

    /*
      Filesystem path to the PKCS#12 (.p12) certificate.
      Efí Bank issues one certificate per account; this may point to the same file
      as {@code efi.certificate-path} or to a separate one per deployment.
     */
    @NotBlank String certificatePath,

    /*
      Password for the PKCS#12 certificate. May be {@code null} or empty for sandbox certs.
     */
    String certificatePassword,

    /*
      Public HTTPS URL that Efí Bank will call for boleto payment notifications.
      Efí sends a POST to this URL with a {@code notification} token each time
      a charge status changes.

      Example: "https://api.our-domain.com/v1/boleto/webhook"
     */
    @NotBlank String notificationUrl,

    /*
      true  → Sandbox (https://cobrancas-h.api.efipay.com.br)
      false → Production (https://cobrancas.api.efipay.com.br)

      Defaults to true in application.yml so a misconfigured deployment
      hits sandbox instead of billing real accounts.
     */
    @NotNull Boolean sandbox,

    /*
      WebClient response timeout in seconds. Default: 30.
      Controls how long the Cobranças WebClient waits for a response from Efí Bank
      before raising a timeout error.
     */
    @Positive int responseTimeoutInSeconds

) implements EfiProperties {
    @Override
    public String baseUrl() {
        return Boolean.TRUE.equals(sandbox)
            ? "https://cobrancas-h.api.efipay.com.br"
            : "https://cobrancas.api.efipay.com.br";
    }
}
