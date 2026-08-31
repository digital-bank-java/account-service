package com.digitalbank.accountservice.adapter.out.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.digitalbank.accountservice.application.port.out.AccountReservationEvent;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "account.reservation.kafka", name = "enabled", havingValue = "true")
class KafkaAccountReservationEventRelay {
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final AccountReservationEventOutbox outbox;
	private final ObjectMapper objectMapper;
	private final Environment environment;
	KafkaAccountReservationEventRelay(@Qualifier("reservationKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate, AccountReservationEventOutbox outbox, ObjectMapper objectMapper, Environment environment) {
		this.kafkaTemplate = kafkaTemplate; this.outbox = outbox; this.objectMapper = objectMapper; this.environment = environment;
	}

	@Scheduled(fixedDelayString = "${account.reservation.kafka.relay-delay-ms:1000}")
	void publishReadyEvents() {
		var now = Instant.now(); var token = UUID.randomUUID();
		for (var event : outbox.claimReady(100, now, token, now.plusSeconds(900))) {
			try { kafkaTemplate.send(record(event)).get(10, TimeUnit.SECONDS); outbox.markPublished(event, token, Instant.now()); }
			catch (Exception exception) { var message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(); outbox.markFailed(event, token, message.substring(0, Math.min(2000, message.length())), Instant.now().plusSeconds(5)); }
		}
	}

	private ProducerRecord<String, String> record(AccountReservationEvent event) {
		var record = new ProducerRecord<>(topic(event), event.sourceAccountId().toString(), serialize(event));
		add(record, "event-id", event.eventId().toString()); add(record, "correlation-id", event.correlationId()); add(record, "causation-id", event.causationId()); add(record, "producer", event.producer()); add(record, "schema-version", event.schemaVersion()); add(record, "occurred-at", event.occurredAt().toString()); return record;
	}
	private String topic(AccountReservationEvent event) {
		return switch (event.eventType()) {
			case "AccountReservationAccepted.v1" -> environment.getRequiredProperty("account.reservation.kafka.accepted-topic");
			case "AccountReservationRejected.v1" -> environment.getRequiredProperty("account.reservation.kafka.rejected-topic");
			case "AccountReservationReleased.v1" -> environment.getRequiredProperty("account.reservation.kafka.released-topic");
			case "AccountReservationExpired.v1" -> environment.getRequiredProperty("account.reservation.kafka.expired-topic");
			default -> throw new IllegalArgumentException("Unsupported reservation event type: " + event.eventType());
		};
	}
	private String serialize(AccountReservationEvent event) { try { return objectMapper.writeValueAsString(event); } catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize reservation event", exception); } }
	private static void add(ProducerRecord<String, String> record, String name, String value) { record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)); }
}
