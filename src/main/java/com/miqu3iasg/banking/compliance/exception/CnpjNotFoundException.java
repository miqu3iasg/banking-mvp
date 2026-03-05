package com.miqu3iasg.banking.compliance.exception;

import com.miqu3iasg.banking.compliance.exception.code.ComplianceFaultCode;
import com.miqu3iasg.banking.shared.exception.BusinessException;

import java.util.Map;

public class CnpjNotFoundException extends BusinessException {
	public CnpjNotFoundException (String cnpj) {
		super(
			"CNPJ not found in Receita Federal: " + cnpj,
			ComplianceFaultCode.CNPJ_NOT_FOUND,
			Map.of("cnpj", cnpj),
			null
		);
	}
}
