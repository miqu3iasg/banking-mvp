package com.miqu3iasg.banking.boleto.domain;

import com.miqu3iasg.banking.boleto.exception.InvalidBoletoStateTransitionException;
import com.miqu3iasg.banking.shared.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "boletos",
	indexes = {
		@Index(name = "idx_boletos_account_date", columnList = "account_id, created_at"),
		@Index(name = "idx_boletos_provider_charge_id", columnList = "provider_charge_id", unique = true),
	}
)
public class Boleto extends AuditableEntity {

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Column(name = "payer_name", nullable = false, length = 200)
	private String payerName;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "street", column = @Column(name = "payer_street", nullable = false, length = 200)),
		@AttributeOverride(name = "number", column = @Column(name = "payer_number", nullable = false, length = 20)),
		@AttributeOverride(name = "neighborhood", column = @Column(name = "payer_neighborhood", nullable = false, length = 100)),
		@AttributeOverride(name = "zipcode", column = @Column(name = "payer_zipcode", nullable = false, length = 8)),
		@AttributeOverride(name = "city", column = @Column(name = "payer_city", nullable = false, length = 100)),
		@AttributeOverride(name = "state", column = @Column(name = "payer_state", nullable = false, length = 2))
	})
	private Address payerAddress;

	@Column(name = "payer_document", nullable = false, length = 14)
	private String payerDocument;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(nullable = false, length = 500)
	private String description;

	/**
	 * Numeric charge ID returned by Efí Bank after issuance.
	 * Used to correlate with webhook notifications.
	 */
	@Column(name = "provider_charge_id")
	private Long providerChargeId;

	/**
	 * The barcode (linha digitável) of the boleto.
	 * Example: "00000.00000 00000.000000 00000.000000 0 00000000000000"
	 */
	@Column(columnDefinition = "TEXT")
	private String barcode;

	/**
	 * Public link to the Bolix (PDF/HTML view) of the boleto.
	 */
	@Column(name = "billet_link", columnDefinition = "TEXT")
	private String billetLink;

	@Column(name = "pdf_url", columnDefinition = "TEXT")
	private String pdfUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BoletoStatus status;

	@Column(name = "paid_at")
	private Instant paidAt;

	public static Boleto issue (
		UUID accountId,
		String payerName,
		String payerDocument,
		Address payerAddress,
		BigDecimal amount,
		LocalDate dueDate,
		String description
	) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Boleto amount must be positive, got: " + amount);
		}
		if (dueDate == null || !dueDate.isAfter(LocalDate.now())) {
			throw new IllegalArgumentException("Boleto due date must be in the future, got: " + dueDate);
		}

		var boleto = new Boleto();
		boleto.accountId = accountId;
		boleto.payerName = payerName;
		boleto.payerDocument = payerDocument;
		boleto.payerAddress = payerAddress;
		boleto.amount = amount;
		boleto.dueDate = dueDate;
		boleto.description = description;
		boleto.status = BoletoStatus.PENDING;
		return boleto;
	}

	public void enrichWithProviderData (long providerChargeId, String barcode, String billetLink, String pdfUrl) {
		this.providerChargeId = providerChargeId;
		this.barcode = barcode;
		this.billetLink = billetLink;
		this.pdfUrl = pdfUrl;
	}

	public void markAsPaid (Instant paidAt) {
		requireCanTransitionTo(BoletoStatus.PAID);
		this.status = BoletoStatus.PAID;
		this.paidAt = paidAt;
	}

	public void expire () {
		requireCanTransitionTo(BoletoStatus.EXPIRED);
		this.status = BoletoStatus.EXPIRED;
	}

	public boolean isExpired () {
		return status == BoletoStatus.PENDING && LocalDate.now().isAfter(dueDate);
	}

	public boolean isPaid () {
		return status == BoletoStatus.PAID;
	}

	private void requireCanTransitionTo (BoletoStatus target) {
		if (!this.status.canTransitionTo(target)) {
			throw new InvalidBoletoStateTransitionException(
				providerChargeId != null ? providerChargeId.toString() : "(unpersisted)",
				this.status,
				target
			);
		}
	}
}
