package com.digitalbank.accountservice.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;

public record ReservationView(
		UUID reservationId,
		AccountId accountId,
		String reservationRequestId,
		String currency,
		BigDecimal amount,
		String correlationId,
		String causationId,
		ReservationStatus status,
		Instant expiresAt,
		long version,
		Instant createdAt,
		Instant updatedAt,
		String ledgerPostingId,
		String reversedByLedgerPostingId) {

	public ReservationView(
				UUID reservationId,
				AccountId accountId,
				String reservationRequestId,
				String currency,
				BigDecimal amount,
				String correlationId,
				String causationId,
				ReservationStatus status,
				Instant expiresAt,
				long version,
				Instant createdAt,
				Instant updatedAt) {
		this(reservationId, accountId, reservationRequestId, currency, amount, correlationId, causationId,
				status, expiresAt, version, createdAt, updatedAt, null, null);
	}
}
