package com.digitalbank.accountservice.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.digitalbank.accountservice.application.port.in.ReservationInboxEventView;
import com.digitalbank.accountservice.application.port.in.ReservationReleaseRequestedEvent;
import com.digitalbank.accountservice.application.port.in.ReservationRequestedEvent;
import com.digitalbank.accountservice.application.port.out.ReservationCommandInboxRepository;

@Repository
class PostgresReservationCommandInboxRepository implements ReservationCommandInboxRepository {
	private final SpringDataReservationCommandInboxRepository repository;

	PostgresReservationCommandInboxRepository(SpringDataReservationCommandInboxRepository repository) { this.repository = repository; }

	@Override public Optional<ReservationInboxEventView> findByEventId(UUID eventId) { return repository.findById(eventId).map(ReservationCommandInboxJpaMapper::toView); }
	@Override public Optional<ReservationInboxEventView> findByReservationRequestId(String id) { return repository.findByReservationRequestId(id).map(ReservationCommandInboxJpaMapper::toView); }
	@Override public ReservationInboxEventView save(ReservationRequestedEvent event, Instant processedAt) { return save(event, processedAt, null); }
	@Override public ReservationInboxEventView save(ReservationRequestedEvent event, Instant processedAt, UUID reservationId) {
		return ReservationCommandInboxJpaMapper.toView(repository.saveAndFlush(ReservationCommandInboxJpaMapper.toEntity(event, processedAt, reservationId)));
	}
	@Override public ReservationInboxEventView save(ReservationReleaseRequestedEvent event, Instant processedAt) {
		return ReservationCommandInboxJpaMapper.toView(repository.saveAndFlush(ReservationCommandInboxJpaMapper.toEntity(event, processedAt)));
	}
}
