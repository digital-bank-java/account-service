package com.digitalbank.accountservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.digitalbank.accountservice.application.port.in.ReservationRequestedEvent;
import com.digitalbank.accountservice.domain.exception.InvalidReservationEventException;

class ReservationEventParserTest {

	private final ReservationEventParser parser = new ReservationEventParser();

	@Test
	void parsesGovernedReservationRequestAndRetainsPayloadFingerprint() {
		var payload = """
				{"eventId":"10000000-0000-4000-8000-000000000001","eventType":"AccountReservationRequested.v1","schemaVersion":"1.0.0","producer":"transaction-service","occurredAt":"2026-09-01T10:15:30Z","aggregateId":"reservation-001","correlationId":"correlation-001","causationId":"transfer-command-001","transactionId":"40000000-0000-4000-8000-000000000001","reservationRequestId":"reservation-001","sourceAccountId":"50000000-0000-4000-8000-000000000001","destinationAccountId":"50000000-0000-4000-8000-000000000002","amount":"125.5000","currency":"USD","expiresAt":"2026-09-01T10:20:30Z"}
				""";

		var event = (ReservationRequestedEvent) parser.parse(payload, headers("10000000-0000-4000-8000-000000000001"),
				"AccountReservationRequested.v1");

		assertThat(event).isInstanceOf(ReservationRequestedEvent.class);
		assertThat(event.eventId()).hasToString("10000000-0000-4000-8000-000000000001");
		assertThat(event.amount()).isEqualByComparingTo("125.5000");
		assertThat(event.payloadFingerprint()).hasSize(64);
	}

	@Test
	void rejectsHeaderPayloadIdentityMismatchAndTrailingJson() {
		var payload = """
				{"eventId":"10000000-0000-4000-8000-000000000001","eventType":"AccountReservationRequested.v1","schemaVersion":"1.0.0","producer":"transaction-service","occurredAt":"2026-09-01T10:15:30Z","aggregateId":"reservation-001","correlationId":"correlation-001","causationId":"transfer-command-001","transactionId":"40000000-0000-4000-8000-000000000001","reservationRequestId":"reservation-001","sourceAccountId":"50000000-0000-4000-8000-000000000001","destinationAccountId":"50000000-0000-4000-8000-000000000002","amount":"1.0000","currency":"USD","expiresAt":"2026-09-01T10:20:30Z"} trailing
				""";

		assertThatThrownBy(() -> parser.parse(payload, headers("10000000-0000-4000-8000-000000000009"),
				"AccountReservationRequested.v1"))
				.isInstanceOf(InvalidReservationEventException.class);
	}

	private static Map<String, String> headers(String eventId) {
		return Map.of(
				"event-id", eventId,
				"correlation-id", "correlation-001",
				"causation-id", "transfer-command-001",
				"producer", "transaction-service",
				"schema-version", "1.0.0",
				"occurred-at", "2026-09-01T10:15:30Z");
	}
}
