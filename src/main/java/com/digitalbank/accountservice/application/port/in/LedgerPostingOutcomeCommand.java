package com.digitalbank.accountservice.application.port.in;

import java.util.Objects;

public record LedgerPostingOutcomeCommand(
		String eventId,
		String ledgerPostingId,
		String reservationRequestId,
		LedgerPostingOutcome outcome,
		String originalPostingId) {

	public LedgerPostingOutcomeCommand {
		eventId = requireText(eventId, "Event id is required");
		ledgerPostingId = requireText(ledgerPostingId, "Ledger posting id is required");
		reservationRequestId = requireText(reservationRequestId, "Reservation request id is required");
		Objects.requireNonNull(outcome, "Ledger posting outcome is required");
		if (originalPostingId != null) {
			originalPostingId = requireText(originalPostingId, "Original posting id is required");
		}
		if (outcome == LedgerPostingOutcome.REVERSED && originalPostingId == null) {
			throw new IllegalArgumentException("Reversed outcome requires original posting id");
		}
		if (outcome != LedgerPostingOutcome.REVERSED && originalPostingId != null) {
			throw new IllegalArgumentException("Only reversed outcome may include original posting id");
		}
		if (ledgerPostingId.equals(originalPostingId)) {
			throw new IllegalArgumentException("Original posting id must differ from ledger posting id");
		}
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
