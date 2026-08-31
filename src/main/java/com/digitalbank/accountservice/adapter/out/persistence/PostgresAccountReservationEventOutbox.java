package com.digitalbank.accountservice.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.digitalbank.accountservice.application.port.out.AccountReservationEvent;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
class PostgresAccountReservationEventOutbox implements AccountReservationEventOutbox {
	private final SpringDataAccountReservationEventOutboxRepository repository;
	private final ObjectMapper objectMapper;

	PostgresAccountReservationEventOutbox(SpringDataAccountReservationEventOutboxRepository repository, ObjectMapper objectMapper) {
		this.repository = repository; this.objectMapper = objectMapper;
	}

	@Override
	public boolean recordIfAbsent(AccountReservationEvent event) {
		if (repository.existsById(event.eventId())) return false;
		try {
			repository.saveAndFlush(new AccountReservationEventOutboxJpaEntity(event, objectMapper.writeValueAsString(event), Instant.now()));
			return true;
		} catch (org.springframework.dao.DataIntegrityViolationException exception) {
			return false;
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not serialize reservation event", exception);
		}
	}

	@Override
	@Transactional
	public List<AccountReservationEvent> claimReady(int limit, Instant now, UUID token, Instant lease) {
		repository.claimReady(limit, now, token, lease);
		return repository.findByProcessingTokenOrderByCreatedAtAsc(token).stream().map(AccountReservationEventOutboxJpaEntity::toEvent).toList();
	}

	@Override
	@Transactional
	public void markPublished(AccountReservationEvent event, UUID token, Instant publishedAt) {
		repository.markPublished(event.eventId(), token, publishedAt);
	}

	@Override
	@Transactional
	public void markFailed(AccountReservationEvent event, UUID token, String error, Instant retryAt) {
		repository.markFailed(event.eventId(), token, error, retryAt);
	}
}
