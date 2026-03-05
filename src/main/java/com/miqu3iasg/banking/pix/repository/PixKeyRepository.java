package com.miqu3iasg.banking.pix.repository;

import com.miqu3iasg.banking.pix.domain.PixKey;
import com.miqu3iasg.banking.pix.domain.PixKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PixKeyRepository extends JpaRepository<PixKey, UUID> {
	boolean existsByValueAndStatus (String value, PixKeyStatus status);

	List<PixKey> findByAccountIdAndStatus (UUID accountId, PixKeyStatus status);

	Optional<PixKey> findByValueAndStatus (String value, PixKeyStatus status);

	List<PixKey> findAllByStatus (PixKeyStatus status);
}
