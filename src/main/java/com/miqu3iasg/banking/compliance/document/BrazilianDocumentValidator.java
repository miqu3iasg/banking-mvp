package com.miqu3iasg.banking.compliance.document;

import com.miqu3iasg.banking.compliance.exception.InvalidCnpjException;
import com.miqu3iasg.banking.compliance.exception.InvalidCpfException;
import org.springframework.stereotype.Component;

@Component
public class BrazilianDocumentValidator implements DocumentValidator {

	@Override
	public void validate (String documentNumber) {
		if (documentNumber == null || documentNumber.isBlank()) {
			throw new InvalidCpfException(documentNumber, "document number must not be blank");
		}

		String digits = documentNumber.replaceAll("\\D", "");

		if (digits.length() == 11) {
			validateCpf(documentNumber, digits);
		} else if (digits.length() == 14) {
			validateCnpj(documentNumber, digits);
		} else {
			throw new InvalidCpfException(documentNumber, "document must be a valid CPF (11 digits) or CNPJ (14 digits)");
		}
	}

	private void validateCpf (String original, String digits) {
		if (hasAllSameDigits(digits)) {
			throw new InvalidCpfException(original, "all digits are identical");
		}

		int first = computeCpfDigit(digits, 9);
		int second = computeCpfDigit(digits, 10);

		if (digits.charAt(9) - '0' != first || digits.charAt(10) - '0' != second) {
			throw new InvalidCpfException(original, "check digits do not match");
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

	private void validateCnpj (String original, String digits) {
		if (hasAllSameDigits(digits)) {
			throw new InvalidCnpjException(original, "all digits are identical");
		}

		int first = computeCnpjDigit(digits, new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
		int second = computeCnpjDigit(digits, new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});

		if (digits.charAt(12) - '0' != first || digits.charAt(13) - '0' != second) {
			throw new InvalidCnpjException(original, "check digits do not match");
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
