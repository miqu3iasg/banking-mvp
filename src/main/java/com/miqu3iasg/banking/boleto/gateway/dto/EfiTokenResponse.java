package com.miqu3iasg.banking.boleto.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EfiTokenResponse(
	@JsonProperty("access_token") String accessToken,
	@JsonProperty("token_type") String tokenType,
	@JsonProperty("expires_in") int expiresIn,
	String scope
) { }
