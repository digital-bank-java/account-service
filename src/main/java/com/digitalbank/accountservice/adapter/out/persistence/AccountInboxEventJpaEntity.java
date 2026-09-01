package com.digitalbank.accountservice.adapter.out.persistence;

import java.time.Instant;

import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_inbox_events")
class AccountInboxEventJpaEntity {

	@Id
	@Column(name = "event_id", nullable = false, length = 100)
	private String eventId;

	@Column(name = "ledger_posting_id", nullable = false, unique = true, length = 100)
	private String ledgerPostingId;

	@Column(name = "reservation_request_id", nullable = false, length = 100)
	private String reservationRequestId;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, length = 30)
	private LedgerPostingOutcome outcome;

	@Column(name = "original_posting_id", length = 100)
	private String originalPostingId;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected AccountInboxEventJpaEntity() {
	}

	AccountInboxEventJpaEntity(
			String eventId,
			String ledgerPostingId,
			String reservationRequestId,
			LedgerPostingOutcome outcome,
			String originalPostingId,
			Instant processedAt) {
		this.eventId = eventId;
		this.ledgerPostingId = ledgerPostingId;
		this.reservationRequestId = reservationRequestId;
		this.outcome = outcome;
		this.originalPostingId = originalPostingId;
		this.processedAt = processedAt;
	}

	String eventId() {
		return eventId;
	}

	String ledgerPostingId() {
		return ledgerPostingId;
	}

	String reservationRequestId() {
		return reservationRequestId;
	}

	LedgerPostingOutcome outcome() {
		return outcome;
	}

	String originalPostingId() {
		return originalPostingId;
	}

	Instant processedAt() {
		return processedAt;
	}
}
