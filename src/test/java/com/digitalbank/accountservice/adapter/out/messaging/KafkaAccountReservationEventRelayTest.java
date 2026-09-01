package com.digitalbank.accountservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.env.MockEnvironment;

import com.digitalbank.accountservice.application.port.out.AccountReservationEvent;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventOutbox;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventOutboxEntry;

class KafkaAccountReservationEventRelayTest {

	@Test
	void publishesStoredPayloadAndUsesAggregateIdAsKafkaKey() {
		var event = new AccountReservationEvent(
				UUID.randomUUID(), "AccountReservationAccepted.v1", "1.0.0", "account-service",
				Instant.parse("2026-09-01T10:15:30Z"), "reservation-request-001", "correlation-001",
				"causation-001", UUID.randomUUID(), "reservation-request-001", UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25.0000"), "AED",
				Instant.parse("2026-09-01T10:20:30Z"), "ACTIVE", null, null, null, null);
		var storedPayload = "{\"eventType\":\"AccountReservationAccepted.v1\",\"amount\":\"25.0000\"}";
		var outbox = new RecordingOutbox(new AccountReservationEventOutboxEntry(event, storedPayload));
		var kafkaTemplate = new RecordingKafkaTemplate();
		var environment = new MockEnvironment()
				.withProperty("account.reservation.kafka.accepted-topic", "account.reservation.accepted.v1");
		var relay = new KafkaAccountReservationEventRelay(kafkaTemplate, outbox, environment);

		relay.publishReadyEvents();

		var record = kafkaTemplate.record();
		assertThat(record.key()).isEqualTo(event.aggregateId());
		assertThat(record.value()).isEqualTo(storedPayload);
	}

	private static final class RecordingKafkaTemplate extends KafkaTemplate<String, String> {
		private ProducerRecord<String, String> record;

		private RecordingKafkaTemplate() {
			super(new DefaultKafkaProducerFactory<>(Map.of(), new StringSerializer(), new StringSerializer()));
		}

		@Override
		public CompletableFuture<SendResult<String, String>> send(ProducerRecord<String, String> record) {
			this.record = record;
			return CompletableFuture.completedFuture(null);
		}

		private ProducerRecord<String, String> record() {
			return record;
		}
	}

	private static final class RecordingOutbox implements AccountReservationEventOutbox {
		private final List<AccountReservationEventOutboxEntry> entries;

		private RecordingOutbox(AccountReservationEventOutboxEntry entry) {
			this.entries = List.of(entry);
		}

		@Override
		public boolean recordIfAbsent(AccountReservationEvent event) {
			return false;
		}

		@Override
		public List<AccountReservationEventOutboxEntry> claimReady(
				int limit, Instant now, UUID claimToken, Instant leaseUntil) {
			return entries;
		}
	}
}
