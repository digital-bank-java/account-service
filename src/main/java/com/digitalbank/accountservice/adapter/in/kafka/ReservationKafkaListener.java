package com.digitalbank.accountservice.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.digitalbank.accountservice.application.port.in.ReservationReleaseRequestedEvent;
import com.digitalbank.accountservice.application.port.in.ReservationRequestedEvent;
import com.digitalbank.accountservice.application.service.AccountReservationTransportService;

@Component
@ConditionalOnProperty(prefix = "account.reservation.kafka", name = "enabled", havingValue = "true")
class ReservationKafkaListener {
	private final ReservationEventParser parser;
	private final AccountReservationTransportService service;
	ReservationKafkaListener(ReservationEventParser parser, AccountReservationTransportService service) { this.parser = parser; this.service = service; }

	@KafkaListener(topics = "${account.reservation.kafka.requested-topic}", groupId = "${account.reservation.kafka.group-id}", containerFactory = "reservationKafkaListenerContainerFactory")
	void consumeRequested(ConsumerRecord<String, String> record) {
		service.handle((ReservationRequestedEvent) parser.parse(record.value(), headers(record), "AccountReservationRequested.v1"));
	}

	@KafkaListener(topics = "${account.reservation.kafka.release-requested-topic}", groupId = "${account.reservation.kafka.group-id}", containerFactory = "reservationKafkaListenerContainerFactory")
	void consumeReleaseRequested(ConsumerRecord<String, String> record) {
		service.handle((ReservationReleaseRequestedEvent) parser.parse(record.value(), headers(record), "AccountReservationReleaseRequested.v1"));
	}

	private static Map<String, String> headers(ConsumerRecord<String, String> record) {
		var values = new LinkedHashMap<String, String>(); record.headers().forEach(header -> values.put(header.key(), new String(header.value(), StandardCharsets.UTF_8))); return Map.copyOf(values);
	}
}
