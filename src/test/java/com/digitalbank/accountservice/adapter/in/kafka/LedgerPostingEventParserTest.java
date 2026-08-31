package com.digitalbank.accountservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.domain.exception.InvalidLedgerPostingEventException;

class LedgerPostingEventParserTest {

	private static final UUID EVENT_ID = UUID.fromString("fbd2e85a-bc91-4dc0-a1b3-9509d0d6c241");
	private static final UUID POSTING_ID = UUID.fromString("dd480939-2dde-49b7-aa22-c8f22a7789ad");
	private static final UUID ACCOUNT_ID = UUID.fromString("f5aa4b14-6616-4c84-b0d5-3f178cb50864");
	private static final Instant OCCURRED_AT = Instant.parse("2026-08-30T08:15:30Z");

	private final LedgerPostingEventParser parser = new LedgerPostingEventParser();

	@Test
	void parsesGovernedCompletedEventWhenRequiredHeadersMatchPayload() {
		var event = parser.parse(completedPayload(), headers("ledger-service"), "LedgerPostingCompleted.v1");

		assertThat(event).isEqualTo(new GovernedLedgerPostingEvent.Completed(
				new GovernedLedgerPostingEvent.Metadata(
						EVENT_ID,
						"transfer-58e271cd",
						"command-63ca8eb6",
						"ledger-service",
						"1.0.0",
						OCCURRED_AT),
				POSTING_ID,
				"transfer-58e271cd",
				"reservation-58e271cd",
				POSTING_ID,
				"posting-request-4d93c803",
				null,
				"USD",
				java.util.List.of(
						new GovernedLedgerPostingEvent.Line(ACCOUNT_ID, "DEBIT", "125.5000"),
						new GovernedLedgerPostingEvent.Line(UUID.fromString("68bc664d-75f7-46b7-82bf-659e733eb653"), "CREDIT", "125.5000"))));
	}

	@Test
	void rejectsCompletedEventFromAnUntrustedProducer() {
		assertThatThrownBy(() -> parser.parse(completedPayload(), headers("unknown-service"), "LedgerPostingCompleted.v1"))
				.isInstanceOf(InvalidLedgerPostingEventException.class);
	}

	@Test
	void parsesGovernedFailedEventWhenRequiredHeadersMatchPayload() {
		var event = parser.parse(failedPayload(), headers("ledger-service"), "LedgerPostingFailed.v1");

		assertThat(event).isEqualTo(new GovernedLedgerPostingEvent.Failed(
				new GovernedLedgerPostingEvent.Metadata(
						EVENT_ID,
						"transfer-58e271cd",
						"command-63ca8eb6",
						"ledger-service",
						"1.0.0",
						OCCURRED_AT),
				"posting-request-4d93c803",
				"transfer-58e271cd",
				"reservation-58e271cd",
				"posting-request-4d93c803",
				"VALIDATION_ERROR",
				"Posting lines are invalid."));
	}

	@Test
	void rejectsCompletedEventWithUnbalancedLines() {
		var unbalancedPayload = completedPayload().replace(
				"\"lineType\": \"CREDIT\", \"amount\": \"125.5000\"",
				"\"lineType\": \"CREDIT\", \"amount\": \"124.0000\"");

		assertThatThrownBy(() -> parser.parse(unbalancedPayload, headers("ledger-service"), "LedgerPostingCompleted.v1"))
				.isInstanceOf(InvalidLedgerPostingEventException.class);
	}

	@Test
	void rejectsCompletedEventWhoseAggregateDoesNotMatchPostingIdentity() {
		var wrongAggregate = completedPayload().replaceFirst(
				"\"aggregateId\": \"" + POSTING_ID,
				"\"aggregateId\": \"13dc863f-30f4-4ed4-b9a6-3035d310d7ae");

		assertThatThrownBy(() -> parser.parse(wrongAggregate, headers("ledger-service"), "LedgerPostingCompleted.v1"))
				.isInstanceOf(InvalidLedgerPostingEventException.class);
	}

	private static Map<String, String> headers(String producer) {
		return Map.of(
				"event-id", EVENT_ID.toString(),
				"correlation-id", "transfer-58e271cd",
				"causation-id", "command-63ca8eb6",
				"producer", producer,
				"schema-version", "1.0.0",
				"occurred-at", OCCURRED_AT.toString());
	}

	private static String completedPayload() {
		return """
				{
				  "eventId": "%s",
				  "eventType": "LedgerPostingCompleted.v1",
				  "schemaVersion": "1.0.0",
				  "producer": "ledger-service",
				  "occurredAt": "%s",
				  "aggregateId": "%s",
				  "correlationId": "transfer-58e271cd",
				  "causationId": "command-63ca8eb6",
				  "transactionId": "transfer-58e271cd",
				  "reservationRequestId": "reservation-58e271cd",
				  "postingId": "%s",
				  "postingRequestId": "posting-request-4d93c803",
				  "currency": "USD",
				  "lines": [
				    { "accountId": "%s", "lineType": "DEBIT", "amount": "125.5000" },
				    { "accountId": "68bc664d-75f7-46b7-82bf-659e733eb653", "lineType": "CREDIT", "amount": "125.5000" }
				  ]
				}
				""".formatted(EVENT_ID, OCCURRED_AT, POSTING_ID, POSTING_ID, ACCOUNT_ID);
	}

	private static String failedPayload() {
		return """
				{
				  "eventId": "%s",
				  "eventType": "LedgerPostingFailed.v1",
				  "schemaVersion": "1.0.0",
				  "producer": "ledger-service",
				  "occurredAt": "%s",
				  "aggregateId": "posting-request-4d93c803",
				  "correlationId": "transfer-58e271cd",
				  "causationId": "command-63ca8eb6",
				  "transactionId": "transfer-58e271cd",
				  "reservationRequestId": "reservation-58e271cd",
				  "postingRequestId": "posting-request-4d93c803",
				  "failureCode": "VALIDATION_ERROR",
				  "failureReason": "Posting lines are invalid."
				}
				""".formatted(EVENT_ID, OCCURRED_AT);
	}
}
