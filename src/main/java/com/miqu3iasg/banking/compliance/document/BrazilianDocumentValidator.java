package com.miqu3iasg.banking.compliance.document;

import com.miqu3iasg.banking.shared.exception.InvalidDocumentException;
import org.springframework.stereotype.Component;

@Component
public class BrazilianDocumentValidator implements DocumentValidator {

	@Override
	public void validate (String documentNumber) {
		if (documentNumber == null || documentNumber.isBlank()) {
			throw new InvalidDocumentException("Document number must not be blank");
		}

		String digits = documentNumber.replaceAll("\\D", "");

		if (digits.length() == 11) {
			validateCpf(digits);
		} else if (digits.length() == 14) {
			validateCnpj(digits);
		} else {
			throw new InvalidDocumentException("Document must be a valid CPF (11 digits) or CNPJ (14 digits)");
		}
	}

	private void validateCpf (String digits) {
		if (hasAllSameDigits(digits)) {
			throw new InvalidDocumentException("CPF is invalid");
		}

		int first = computeCpfDigit(digits, 9);
		int second = computeCpfDigit(digits, 10);

		if (digits.charAt(9) - '0' != first || digits.charAt(10) - '0' != second) {
			throw new InvalidDocumentException("CPF check digits are invalid");
		}
	}

	private int computeCpfDigit (String digits, int length) {
		int sum = 0;

		for (int i = 0; i < length; i++) {
			sum += (digits.charAt(i) - '0') * (length + 1 - i);
		}

		int remainder = sum % 11;

		return remainder < 2 ? 0 : 11 - remainder;
	}

	private void validateCnpj (String digits) {
		if (hasAllSameDigits(digits)) {
			throw new InvalidDocumentException("CNPJ is invalid");
		}

		int first = computeCnpjDigit(digits, new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
		int second = computeCnpjDigit(digits, new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});

		if (digits.charAt(12) - '0' != first || digits.charAt(13) - '0' != second) {
			throw new InvalidDocumentException("CNPJ check digits are invalid");
		}
	}

	private int computeCnpjDigit (String digits, int[] weights) {
		int sum = 0;

		for (int i = 0; i < weights.length; i++) {
			sum += (digits.charAt(i) - '0') * weights[i];
		}

		int remainder = sum % 11;

		return remainder < 2 ? 0 : 11 - remainder;
	}

	private boolean hasAllSameDigits (String digits) {
		return digits.chars().distinct().count() == 1;
	}
}
