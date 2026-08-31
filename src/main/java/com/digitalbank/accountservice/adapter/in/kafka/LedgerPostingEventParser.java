package com.digitalbank.accountservice.adapter.in.kafka;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.domain.exception.InvalidLedgerPostingEventException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LedgerPostingEventParser {

	private static final String COMPLETED_EVENT_TYPE = "LedgerPostingCompleted.v1";
	private static final String FAILED_EVENT_TYPE = "LedgerPostingFailed.v1";
	private static final String TRUSTED_PRODUCER = "ledger-service";
	private static final String SUPPORTED_SCHEMA_VERSION = "1.0.0";

	private final ObjectMapper objectMapper = new ObjectMapper();

	public GovernedLedgerPostingEvent parse(String payload, Map<String, String> headers, String expectedEventType) {
		try {
			var root = objectMapper.readTree(payload);
			if (!expectedEventType.equals(text(root, "eventType"))) {
				throw new InvalidLedgerPostingEventException("Unsupported ledger event type");
			}
			var metadata = metadata(root, headers);
			return switch (expectedEventType) {
				case COMPLETED_EVENT_TYPE -> completed(root, metadata);
				case FAILED_EVENT_TYPE -> failed(root, metadata);
				default -> throw new InvalidLedgerPostingEventException("Unsupported ledger event type");
			};
		} catch (InvalidLedgerPostingEventException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new InvalidLedgerPostingEventException("Malformed ledger event payload");
		}
	}

	private static GovernedLedgerPostingEvent.Completed completed(
			JsonNode root,
			GovernedLedgerPostingEvent.Metadata metadata) {
		var aggregateId = uuid(root, "aggregateId");
		var postingId = uuid(root, "postingId");
		if (!aggregateId.equals(postingId)) {
			throw new InvalidLedgerPostingEventException("Completed event aggregate id must equal posting id");
		}
		return new GovernedLedgerPostingEvent.Completed(
				metadata, aggregateId, text(root, "transactionId"), text(root, "reservationRequestId"), postingId,
				text(root, "postingRequestId"), optionalUuid(root, "reversalOfLedgerEntryId"), text(root, "currency"), lines(root));
	}

	private static GovernedLedgerPostingEvent.Failed failed(
			JsonNode root,
			GovernedLedgerPostingEvent.Metadata metadata) {
		var aggregateId = text(root, "aggregateId");
		var postingRequestId = text(root, "postingRequestId");
		var failureCode = text(root, "failureCode");
		var failureReason = text(root, "failureReason");
		if (!aggregateId.equals(postingRequestId)
				|| !java.util.Set.of("VALIDATION_ERROR", "CONFLICT", "ACCOUNTING_ERROR", "INTERNAL_ERROR").contains(failureCode)
				|| failureReason.length() > 500) {
			throw new InvalidLedgerPostingEventException("Invalid governed failed posting event");
		}
		return new GovernedLedgerPostingEvent.Failed(
				metadata, aggregateId, text(root, "transactionId"), text(root, "reservationRequestId"),
				postingRequestId, failureCode, failureReason);
	}

	private static GovernedLedgerPostingEvent.Metadata metadata(JsonNode root, Map<String, String> headers) {
		var eventId = UUID.fromString(header(headers, "event-id"));
		var correlationId = header(headers, "correlation-id");
		var causationId = header(headers, "causation-id");
		var producer = header(headers, "producer");
		var schemaVersion = header(headers, "schema-version");
		var occurredAt = Instant.parse(header(headers, "occurred-at"));
		if (!TRUSTED_PRODUCER.equals(producer) || !SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)
				|| !eventId.toString().equals(text(root, "eventId"))
				|| !correlationId.equals(text(root, "correlationId"))
				|| !causationId.equals(text(root, "causationId"))
				|| !producer.equals(text(root, "producer"))
				|| !schemaVersion.equals(text(root, "schemaVersion"))
				|| !occurredAt.equals(Instant.parse(text(root, "occurredAt")))) {
			throw new InvalidLedgerPostingEventException("Ledger event headers must match the governed payload metadata");
		}
		return new GovernedLedgerPostingEvent.Metadata(eventId, correlationId, causationId, producer, schemaVersion, occurredAt);
	}

	private static List<GovernedLedgerPostingEvent.Line> lines(JsonNode root) {
		var lines = root.path("lines");
		if (!lines.isArray()) {
			throw new InvalidLedgerPostingEventException("Completed event lines are required");
		}
		var values = new java.util.ArrayList<GovernedLedgerPostingEvent.Line>();
		lines.forEach(line -> values.add(new GovernedLedgerPostingEvent.Line(
				uuid(line, "accountId"), text(line, "lineType"), text(line, "amount"))));
		if (values.size() < 2
				|| values.stream().noneMatch(line -> "DEBIT".equals(line.lineType()))
				|| values.stream().noneMatch(line -> "CREDIT".equals(line.lineType()))
				|| values.stream().anyMatch(line -> !"DEBIT".equals(line.lineType()) && !"CREDIT".equals(line.lineType()))) {
			throw new InvalidLedgerPostingEventException("Completed event requires debit and credit lines");
		}
		var debits = values.stream()
				.filter(line -> "DEBIT".equals(line.lineType()))
				.map(line -> new java.math.BigDecimal(line.amount()))
				.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
		var credits = values.stream()
				.filter(line -> "CREDIT".equals(line.lineType()))
				.map(line -> new java.math.BigDecimal(line.amount()))
				.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
		if (debits.compareTo(credits) != 0) {
			throw new InvalidLedgerPostingEventException("Completed event debit and credit lines must balance");
		}
		return List.copyOf(values);
	}

	private static String header(Map<String, String> headers, String name) {
		var value = headers.get(name);
		if (value == null || value.isBlank()) {
			throw new InvalidLedgerPostingEventException("Required ledger event header is missing: " + name);
		}
		return value;
	}

	private static String text(JsonNode node, String name) {
		var value = node.path(name);
		if (!value.isTextual() || value.asText().isBlank()) {
			throw new InvalidLedgerPostingEventException("Required ledger event field is missing: " + name);
		}
		return value.asText();
	}

	private static UUID uuid(JsonNode node, String name) {
		return UUID.fromString(text(node, name));
	}

	private static UUID optionalUuid(JsonNode node, String name) {
		var value = node.path(name);
		return value.isMissingNode() || value.isNull() ? null : UUID.fromString(text(node, name));
	}
}
