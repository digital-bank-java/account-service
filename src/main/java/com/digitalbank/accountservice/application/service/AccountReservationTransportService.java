package com.digitalbank.accountservice.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.digitalbank.accountservice.application.port.in.ReservationRequestedEvent;
import com.digitalbank.accountservice.application.port.in.ReservationReleaseRequestedEvent;
import com.digitalbank.accountservice.application.port.in.ReservationTransportResult;
import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.out.AccountReservationEvent;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventFactory;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventOutbox;
import com.digitalbank.accountservice.application.port.out.ReservationCommandInboxRepository;
import com.digitalbank.accountservice.domain.exception.ReservationEventConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationNotFoundException;
import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;
import com.digitalbank.accountservice.domain.model.ReservationStatus;

@Service
public class AccountReservationTransportService {

	private final AccountReservationService reservationService;
	private final ReservationCommandInboxRepository inbox;
	private final AccountReservationEventOutbox outbox;
	private final Clock clock;
	private final TransactionOperations transactionOperations;

	@Autowired
	public AccountReservationTransportService(
			AccountReservationService reservationService,
			ReservationCommandInboxRepository inbox,
			AccountReservationEventOutbox outbox,
			Clock clock,
			PlatformTransactionManager transactionManager) {
		this(reservationService, inbox, outbox, clock, new TransactionTemplate(transactionManager));
	}

	public AccountReservationTransportService(
			AccountReservationService reservationService,
			ReservationCommandInboxRepository inbox,
			AccountReservationEventOutbox outbox,
			Clock clock) {
		this(reservationService, inbox, outbox, clock, (TransactionOperations) null);
	}

	private AccountReservationTransportService(
			AccountReservationService reservationService,
			ReservationCommandInboxRepository inbox,
			AccountReservationEventOutbox outbox,
			Clock clock,
			TransactionOperations transactionOperations) {
		this.reservationService = reservationService;
		this.inbox = inbox;
		this.outbox = outbox;
		this.clock = clock;
		this.transactionOperations = transactionOperations;
	}

	public ReservationTransportResult handle(ReservationRequestedEvent event) {
		return executeInTransaction(() -> handleRequest(event));
	}

	public ReservationTransportResult handle(ReservationReleaseRequestedEvent event) {
		return executeInTransaction(() -> handleRelease(event));
	}

	public void recordLedgerFailureRelease(ReservationView reservation, UUID eventId, String postingRequestId, Instant occurredAt) {
		var event = releasedEvent(reservation, eventId.toString(), "LEDGER_POSTING_FAILED", postingRequestId, occurredAt);
		outbox.recordIfAbsent(event);
	}

	private ReservationTransportResult handleRequest(ReservationRequestedEvent event) {
		var existing = inbox.findByEventId(event.eventId());
		if (existing.isPresent()) {
			return replayRequest(existing.orElseThrow(), event);
		}
		var requestReplay = inbox.findByReservationRequestId(event.reservationRequestId());
		if (requestReplay.isPresent()) {
			if (!sameRequest(requestReplay.orElseThrow(), event)) {
				throw new ReservationEventConflictException("Reservation request id already contains different data");
			}
			return replayRequest(requestReplay.orElseThrow(), event);
		}

		var now = clock.instant();
		var attempt = reservationService.reserveForTransport(new com.digitalbank.accountservice.application.port.in.ReserveFundsCommand(
				event.reservationRequestId(), event.sourceAccountId(), event.destinationAccountId(), event.transactionId(),
				event.currency(), event.amount(), event.correlationId(), event.causationId(), event.expiresAt(),
				AccountReservationEventFactory.eventId("AccountReservationAccepted.v1", event.reservationRequestId())));
		if (attempt.accepted()) {
			var reservation = attempt.reservation();
			outbox.recordIfAbsent(acceptedEvent(event, reservation, now));
			inbox.save(event, now, reservation.reservationId());
			return ReservationTransportResult.accepted(event.eventId(), false);
		}

		outbox.recordIfAbsent(rejectedEvent(event, attempt.rejectionCode(), attempt.rejectionReason(), now));
		inbox.save(event, now);
		return ReservationTransportResult.rejected(event.eventId(), false);
	}

	private ReservationTransportResult handleRelease(ReservationReleaseRequestedEvent event) {
		var existing = inbox.findByEventId(event.eventId());
		if (existing.isPresent()) {
			return replayRelease(existing.orElseThrow(), event);
		}
		var now = clock.instant();
		var reservation = reservationService.release(event.sourceAccountId(), event.reservationId(),
				event.reservationRequestId(), now);
		if (reservation.status() == ReservationStatus.ACTIVE || reservation.status() == ReservationStatus.COMMITTED
				|| reservation.status() == ReservationStatus.REVERSED) {
			throw new ReservationStateConflictException(event.reservationRequestId(), reservation.status());
		}
		if (reservation.status() == ReservationStatus.RELEASED) {
			outbox.recordIfAbsent(releasedEvent(reservation, event.eventId().toString(), event.reason(), event.postingRequestId(), now));
		}
		inbox.save(event, now);
		return new ReservationTransportResult(event.eventId(), reservation.status(), false);
	}

	private ReservationTransportResult replayRequest(
			com.digitalbank.accountservice.application.port.in.ReservationInboxEventView existing,
			ReservationRequestedEvent event) {
		if (!sameRequest(existing, event)) {
			throw new ReservationEventConflictException("Reservation request event was reused with different data");
		}
		return existing.reservationId() == null
				? ReservationTransportResult.rejected(event.eventId(), true)
				: ReservationTransportResult.accepted(event.eventId(), true);
	}

	private ReservationTransportResult replayRelease(
			com.digitalbank.accountservice.application.port.in.ReservationInboxEventView existing,
			ReservationReleaseRequestedEvent event) {
		if (!existing.matches(event)) {
			throw new ReservationEventConflictException("Reservation release event was reused with different data");
		}
		return new ReservationTransportResult(event.eventId(), existing.reservationId() == null
				? ReservationStatus.RELEASED : ReservationStatus.RELEASED, true);
	}

	private static boolean sameRequest(
			com.digitalbank.accountservice.application.port.in.ReservationInboxEventView existing,
			ReservationRequestedEvent event) {
		return existing.eventType().equals(event.eventType())
				&& existing.payloadFingerprint().equals(event.payloadFingerprint())
				&& existing.reservationRequestId().equals(event.reservationRequestId())
				&& existing.transactionId().equals(event.transactionId())
				&& existing.correlationId().equals(event.correlationId())
				&& existing.causationId().equals(event.causationId());
	}

	private static AccountReservationEvent acceptedEvent(
			ReservationRequestedEvent event, ReservationView reservation, Instant now) {
		return new AccountReservationEvent(
				AccountReservationEventFactory.eventId("AccountReservationAccepted.v1", event.reservationRequestId()),
				"AccountReservationAccepted.v1", AccountReservationEvent.SCHEMA_VERSION, AccountReservationEvent.PRODUCER,
				now, event.reservationRequestId(), event.correlationId(), event.eventId().toString(), event.transactionId(),
				event.reservationRequestId(), reservation.reservationId(), event.sourceAccountId().value(), event.destinationAccountId().value(),
				event.amount(), event.currency(), event.expiresAt(), "ACTIVE", null, null, null, null);
	}

	private static AccountReservationEvent rejectedEvent(
			ReservationRequestedEvent event, String code, String reason, Instant now) {
		return new AccountReservationEvent(
				AccountReservationEventFactory.eventId("AccountReservationRejected.v1", event.reservationRequestId()),
				"AccountReservationRejected.v1", AccountReservationEvent.SCHEMA_VERSION, AccountReservationEvent.PRODUCER,
				now, event.reservationRequestId(), event.correlationId(), event.eventId().toString(), event.transactionId(),
				event.reservationRequestId(), null, event.sourceAccountId().value(), event.destinationAccountId().value(),
				event.amount(), event.currency(), null, null, code, reason, null, null);
	}

	public static AccountReservationEvent releasedEvent(
			ReservationView reservation, String causationId, String reason, String postingRequestId, Instant now) {
		return new AccountReservationEvent(
				AccountReservationEventFactory.eventId("AccountReservationReleased.v1", reservation.reservationRequestId()),
				"AccountReservationReleased.v1", AccountReservationEvent.SCHEMA_VERSION, AccountReservationEvent.PRODUCER,
				now, reservation.reservationRequestId(), reservation.correlationId(), causationId, reservation.transactionId(),
				reservation.reservationRequestId(), reservation.reservationId(), reservation.accountId().value(),
				reservation.destinationAccountId() == null ? null : reservation.destinationAccountId().value(), reservation.amount(),
				reservation.currency(), null, "RELEASED", null, null, reason, postingRequestId);
	}

	private <T> T executeInTransaction(Supplier<T> operation) {
		if (transactionOperations == null) {
			return operation.get();
		}
		return transactionOperations.execute(status -> operation.get());
	}
}
