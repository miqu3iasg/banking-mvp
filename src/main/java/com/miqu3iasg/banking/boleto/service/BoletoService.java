package com.miqu3iasg.banking.boleto.service;

import com.miqu3iasg.banking.boleto.api.dto.IssueBoletoRequest;
import com.miqu3iasg.banking.boleto.api.dto.IssueBoletoResponse;
import com.miqu3iasg.banking.boleto.config.EfiBoletoProperties;
import com.miqu3iasg.banking.boleto.gateway.BoletoGateway;
import com.miqu3iasg.banking.boleto.metrics.BoletoMetrics;
import com.miqu3iasg.banking.boleto.repository.BoletoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoletoService {
	private final BoletoRepository repository;
	private final BoletoGateway gateway;
	private final EfiBoletoProperties props;
	private final BoletoMetrics metrics;

	@Transactional
	public IssueBoletoResponse issue (IssueBoletoRequest request) {
		return null;
	}
}
