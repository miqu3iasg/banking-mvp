package com.miqu3iasg.banking.compliance.service;

import com.miqu3iasg.banking.compliance.api.dto.CnpjResponse;
import com.miqu3iasg.banking.compliance.document.DocumentValidator;
import com.miqu3iasg.banking.compliance.gateway.ComplianceGateway;
import com.miqu3iasg.banking.compliance.mapper.CnpjResponseMapper;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CnpjComplianceService {

	private static final String CNPJ_SANITIZE_REGEX = "[.\\-/]";
	private static final int CNPJ_EXPECTED_LENGTH = 14;

	private final DocumentValidator documentValidator;
	private final ComplianceGateway complianceGateway;
	private final CnpjResponseMapper mapper;

	@Observed(name = "compliance.cnpj-query", contextualName = "CnpjComplianceService.query")
	public CnpjResponse query (String cnpj) {
		documentValidator.validate(cnpj);

		return fetchFromCacheOrGateway(sanitize(cnpj));
	}

	@Cacheable(value = "cnpj", key = "#sanitizedCnpj")
	private CnpjResponse fetchFromCacheOrGateway (String sanitizedCnpj) {
		if (sanitizedCnpj.length() != CNPJ_EXPECTED_LENGTH) {
			throw new IllegalArgumentException(
				"Sanitized CNPJ must have exactly %d digits, but got %d."
					.formatted(CNPJ_EXPECTED_LENGTH, sanitizedCnpj.length())
			);
		}

		log.info("Querying BrasilAPI for CNPJ: {}", sanitizedCnpj);

		var raw = complianceGateway.fetchCnpj(sanitizedCnpj);

		return mapper.toResponse(raw);
	}

	private String sanitize (String cnpj) {
		return cnpj.replaceAll(CNPJ_SANITIZE_REGEX, "");
	}
}
