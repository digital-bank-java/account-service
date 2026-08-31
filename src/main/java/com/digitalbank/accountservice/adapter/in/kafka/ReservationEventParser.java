package com.digitalbank.accountservice.adapter.in.kafka;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.digitalbank.accountservice.application.port.in.ReservationReleaseRequestedEvent;
import com.digitalbank.accountservice.application.port.in.ReservationRequestedEvent;
import com.digitalbank.accountservice.domain.exception.InvalidReservationEventException;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ReservationEventParser {

	private static final String REQUESTED = "AccountReservationRequested.v1";
	private static final String RELEASE_REQUESTED = "AccountReservationReleaseRequested.v1";
	private static final Pattern DECIMAL = Pattern.compile("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?$");
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	public Object parse(String payload, Map<String, String> headers, String expectedType) {
		try {
			var root = MAPPER.readTree(payload);
			if (!expectedType.equals(text(root, "eventType"))) {
				throw invalid("Unexpected reservation event type");
			}
			var metadata = metadata(root, headers);
			return switch (expectedType) {
				case REQUESTED -> requested(root, metadata, fingerprint(payload));
				case RELEASE_REQUESTED -> releaseRequested(root, metadata, fingerprint(payload));
				default -> throw invalid("Unsupported reservation event type");
			};
		} catch (InvalidReservationEventException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new InvalidReservationEventException("Malformed reservation event payload", exception);
		}
	}

	private static ReservationRequestedEvent requested(JsonNode root, Metadata metadata, String fingerprint) {
		var aggregateId = text(root, "aggregateId");
		var requestId = text(root, "reservationRequestId");
		if (!aggregateId.equals(requestId)) throw invalid("Reservation aggregate id must equal reservation request id");
		return new ReservationRequestedEvent(metadata.eventId(), text(root, "eventType"), metadata.schemaVersion(),
				metadata.producer(), metadata.occurredAt(), aggregateId, metadata.correlationId(), metadata.causationId(),
				uuid(root, "transactionId"), requestId, accountId(root, "sourceAccountId"), accountId(root, "destinationAccountId"),
				amount(root, "amount"), currency(root, "currency"), instant(root, "expiresAt"), fingerprint);
	}

	private static ReservationReleaseRequestedEvent releaseRequested(JsonNode root, Metadata metadata, String fingerprint) {
		var aggregateId = text(root, "aggregateId");
		var requestId = text(root, "reservationRequestId");
		if (!aggregateId.equals(requestId)) throw invalid("Reservation aggregate id must equal reservation request id");
		var reason = text(root, "reason");
		if (!java.util.Set.of("TRANSFER_CANCELED", "TRANSFER_TIMEOUT", "MANUAL_COMPENSATION").contains(reason)) {
			throw invalid("Unsupported reservation release reason");
		}
		return new ReservationReleaseRequestedEvent(metadata.eventId(), text(root, "eventType"), metadata.schemaVersion(),
				metadata.producer(), metadata.occurredAt(), aggregateId, metadata.correlationId(), metadata.causationId(),
				uuid(root, "transactionId"), requestId, uuid(root, "reservationId"), accountId(root, "sourceAccountId"),
				reason, optionalText(root, "postingRequestId"), fingerprint);
	}

	private static Metadata metadata(JsonNode root, Map<String, String> headers) {
		var eventId = uuidText(header(headers, "event-id"));
		var correlationId = header(headers, "correlation-id");
		var causationId = header(headers, "causation-id");
		var producer = header(headers, "producer");
		var schemaVersion = header(headers, "schema-version");
		var occurredAt = Instant.parse(header(headers, "occurred-at"));
		if (!"transaction-service".equals(producer) || !"1.0.0".equals(schemaVersion)
				|| !eventId.toString().equals(text(root, "eventId"))
				|| !correlationId.equals(text(root, "correlationId"))
				|| !causationId.equals(text(root, "causationId"))
				|| !producer.equals(text(root, "producer"))
				|| !schemaVersion.equals(text(root, "schemaVersion"))
				|| !occurredAt.equals(instant(root, "occurredAt"))) {
			throw invalid("Reservation headers must match governed payload metadata");
		}
		return new Metadata(eventId, correlationId, causationId, producer, schemaVersion, occurredAt);
	}

	private static BigDecimal amount(JsonNode root, String name) {
		var value = text(root, name);
		if (!DECIMAL.matcher(value).matches()) throw invalid("Amount must be a positive decimal string");
		try {
			var amount = new BigDecimal(value).setScale(4, RoundingMode.UNNECESSARY);
			if (amount.signum() <= 0) throw invalid("Amount must be positive");
			return amount;
		} catch (ArithmeticException exception) {
			throw invalid("Amount must have no more than 4 decimal places");
		}
	}

	private static String currency(JsonNode root, String name) {
		var value = text(root, name);
		if (!value.matches("[A-Z]{3}")) throw invalid("Currency must be an uppercase ISO currency code");
		return value;
	}

	private static AccountId accountId(JsonNode root, String name) { return new AccountId(uuid(root, name)); }

	private static UUID uuid(JsonNode root, String name) { return uuidText(text(root, name)); }

	private static UUID uuidText(String value) {
		try { return UUID.fromString(value); } catch (IllegalArgumentException exception) { throw invalid("Invalid UUID identifier"); }
	}

	private static Instant instant(JsonNode root, String name) {
		try { return Instant.parse(text(root, name)); } catch (Exception exception) { throw invalid("Invalid timestamp: " + name); }
	}

	private static String header(Map<String, String> headers, String name) {
		var value = headers.get(name);
		if (value == null || value.isBlank()) throw invalid("Required header is missing: " + name);
		return value;
	}

	private static String text(JsonNode node, String name) {
		var value = node.path(name);
		if (!value.isTextual() || value.asText().isBlank()) throw invalid("Required field is missing: " + name);
		return value.asText();
	}

	private static String optionalText(JsonNode node, String name) {
		var value = node.path(name);
		return value.isMissingNode() || value.isNull() ? null : text(node, name);
	}

	private static String fingerprint(String payload) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
		catch (Exception exception) { throw new IllegalStateException("Could not fingerprint reservation event", exception); }
	}

	private static InvalidReservationEventException invalid(String message) { return new InvalidReservationEventException(message); }

	private record Metadata(UUID eventId, String correlationId, String causationId, String producer, String schemaVersion, Instant occurredAt) { }
}
