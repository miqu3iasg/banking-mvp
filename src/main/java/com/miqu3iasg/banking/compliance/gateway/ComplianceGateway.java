package com.miqu3iasg.banking.compliance.gateway;

import com.miqu3iasg.banking.compliance.gateway.dto.BrasilApiCnpjResponse;

public interface ComplianceGateway {
	BrasilApiCnpjResponse fetchCnpj(String cnpj);
}
