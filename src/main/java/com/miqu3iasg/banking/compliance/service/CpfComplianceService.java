package com.miqu3iasg.banking.compliance.service;

import com.miqu3iasg.banking.compliance.api.dto.CpfResponse;
import com.miqu3iasg.banking.compliance.document.DocumentValidator;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CpfComplianceService {

	private static final String CPF_FORMAT = "%s.%s.%s-%s";
	private static final String MOCK_NAME = "Mock Name";
	private static final String MOCK_STATUS = "REGULAR";
	private static final String MOCK_SOURCE = "MOCK";
	private static final boolean MOCK_ACTIVE = true;

	private static final int CPF_EXPECTED_LENGTH = 11;
	private static final int CPF_BLOCK_1_START = 0;
	private static final int CPF_BLOCK_1_END = 3;
	private static final int CPF_BLOCK_2_START = 3;
	private static final int CPF_BLOCK_2_END = 6;
	private static final int CPF_BLOCK_3_START = 6;
	private static final int CPF_BLOCK_3_END = 9;
	private static final int CPF_DIGIT_START = 9;
	private static final int CPF_DIGIT_END = 11;

	private static final String CPF_SANITIZE_REGEX = "[.\\-/]";

	private final DocumentValidator documentValidator;

	@Observed(name = "compliance.cpf-validate", contextualName = "CpfComplianceService.validate")
	public CpfResponse validate (String cpf) {
		documentValidator.validate(cpf);

		String sanitized = sanitize(cpf);

		validateSanitizedLength(sanitized);

		log.info("CPF validated successfully (mock): {}", sanitized);

		return buildMockResponse(sanitized);
	}

	private CpfResponse buildMockResponse (String sanitized) {
		return new CpfResponse(
			formatCpf(sanitized),
			MOCK_NAME,
			MOCK_STATUS,
			MOCK_ACTIVE,
			MOCK_SOURCE,
			Instant.now()
		);
	}

	private String formatCpf (String digits) {
		return CPF_FORMAT.formatted(
			digits.substring(CPF_BLOCK_1_START, CPF_BLOCK_1_END),
			digits.substring(CPF_BLOCK_2_START, CPF_BLOCK_2_END),
			digits.substring(CPF_BLOCK_3_START, CPF_BLOCK_3_END),
			digits.substring(CPF_DIGIT_START, CPF_DIGIT_END)
		);
	}

	private void validateSanitizedLength (String sanitized) {
		if (sanitized.length() != CPF_EXPECTED_LENGTH) {
			throw new IllegalArgumentException(
				"Sanitized CPF must have exactly %d digits, but got %d."
					.formatted(CPF_EXPECTED_LENGTH, sanitized.length())
			);
		}
	}

	private String sanitize (String cpf) {
		return cpf.replaceAll(CPF_SANITIZE_REGEX, "");
	}
}
