package com.miqu3iasg.banking.pix.service;

import com.miqu3iasg.banking.pix.api.dto.PixKeyResponse;
import com.miqu3iasg.banking.pix.api.dto.RegisterPixKeyRequest;
import com.miqu3iasg.banking.pix.domain.PixKey;
import com.miqu3iasg.banking.pix.domain.PixKeyStatus;
import com.miqu3iasg.banking.pix.domain.PixKeyType;
import com.miqu3iasg.banking.pix.gateway.EfiEvpGateway;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
import com.miqu3iasg.banking.pix.exception.PixKeyAlreadyExistsException;
import com.miqu3iasg.banking.pix.exception.PixKeyNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PixKeyService {
	private final PixKeyRepository keyRepository;
	private final EfiEvpGateway efiEvpGateway;

	public PixKeyResponse registerKey (UUID accountId, RegisterPixKeyRequest req) {
		PixKeyType type = PixKeyType.fromString(req.keyType().toUpperCase());

		if (type != PixKeyType.RANDOM && (req.keyValue() == null || req.keyValue().isBlank())) {
			throw new IllegalArgumentException(
				"keyValue is required for key type %s. Register it in the Efí Bank dashboard first."
					.formatted(type)
			);
		}

		String keyValue = type == PixKeyType.RANDOM ? efiEvpGateway.createEvpKey() : req.keyValue();

		if (keyRepository.existsByValueAndStatus(keyValue, PixKeyStatus.ACTIVE)) {
			throw new PixKeyAlreadyExistsException(keyValue);
		}

		PixKey key = PixKey.register(accountId, type, keyValue);

		PixKey saved = keyRepository.save(key);

		log.info("PIX key registered: accountId={} type={} value={}",
			accountId,
			type,
			key.getValue());

		return PixKeyResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<PixKeyResponse> listKeys (UUID accountId) {
		return keyRepository.findByAccountIdAndStatus(accountId, PixKeyStatus.ACTIVE)
			.stream()
			.map(PixKeyResponse::from)
			.toList();
	}

	public void deleteKey (UUID accountId, UUID keyId) {
		var key = keyRepository.findById(keyId)
			.filter(k -> k.getAccountId().equals(accountId))
			.filter(PixKey::isActive)
			.orElseThrow(() -> new PixKeyNotFoundException(keyId.toString()));

		if (key.getType() == PixKeyType.RANDOM) {
			efiEvpGateway.deleteEvpKey(key.getValue());

			log.warn("EVP key permanently deleted from Efí Bank/BACEN DICT: accountId={} keyId={} chave={}",
				accountId,
				keyId,
				key.getValue());
		}

		key.delete();

		log.info("PIX key soft-deleted: accountId={} keyId={} type={}", accountId, keyId, key.getType());
	}
}
