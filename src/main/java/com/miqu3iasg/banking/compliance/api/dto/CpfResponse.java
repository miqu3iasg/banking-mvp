package com.miqu3iasg.banking.compliance.api.dto;

import java.time.Instant;

public record CpfResponse(
	String cpf,
	String name,
	String registrationStatus,
	boolean valid,
	String source,
	Instant queriedAt
) { }
