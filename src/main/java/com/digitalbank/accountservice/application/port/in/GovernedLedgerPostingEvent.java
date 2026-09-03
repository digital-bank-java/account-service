package com.digitalbank.accountservice.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface GovernedLedgerPostingEvent
		permits GovernedLedgerPostingEvent.Completed, GovernedLedgerPostingEvent.Failed {

	Metadata metadata();

	String reservationRequestId();

	record Metadata(
			UUID eventId,
			String correlationId,
			String causationId,
			String producer,
			String schemaVersion,
			Instant occurredAt) {
	}

	record Line(UUID accountId, String lineType, String amount) {
	}

	record Completed(
			Metadata metadata,
			UUID aggregateId,
			String transactionId,
			String reservationRequestId,
			UUID postingId,
			String postingRequestId,
			UUID reversalOfLedgerEntryId,
			String currency,
			List<Line> lines) implements GovernedLedgerPostingEvent {
	}

	record Failed(
			Metadata metadata,
			String aggregateId,
			String transactionId,
			String reservationRequestId,
			String postingRequestId,
			String failureCode,
			String failureReason) implements GovernedLedgerPostingEvent {
	}
}
