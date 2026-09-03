package com.digitalbank.accountservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.digitalbank.accountservice.application.port.in.ReservationRequestedEvent;
import com.digitalbank.accountservice.application.port.in.ReservationTransportResult;
import com.digitalbank.accountservice.application.port.out.AccountInboxEventRepository;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventOutbox;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.application.port.out.ReservationCommandInboxRepository;
import com.digitalbank.accountservice.domain.exception.ReservationEventConflictException;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

class AccountReservationTransportServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-01T10:15:30Z");
	private InMemoryAccountRepository accounts;
	private InMemoryReservationRepository reservations;
	private InMemoryCommandInbox inbox;
	private RecordingOutbox outbox;
	private AccountReservationService reservationService;
	private AccountReservationTransportService service;

	@BeforeEach
	void setUp() {
		accounts = new InMemoryAccountRepository();
		reservations = new InMemoryReservationRepository();
		inbox = new InMemoryCommandInbox();
		outbox = new RecordingOutbox();
		reservationService = new AccountReservationService(accounts, reservations, Clock.fixed(NOW, ZoneOffset.UTC));
		service = new AccountReservationTransportService(
				reservationService, inbox, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void acceptedRequestRecordsOneAcceptedFactAndExactRedeliveryIsNoOp() {
		var account = account("100.00", "75.00");
		accounts.save(account);
		var event = request(account, "reservation-001", "25.00", "fingerprint-001");

		var first = service.handle(event);
		var replay = service.handle(event);

		assertThat(first).isEqualTo(ReservationTransportResult.accepted(event.eventId(), false));
		assertThat(replay.duplicate()).isTrue();
		assertThat(outbox.events()).hasSize(1);
		assertThat(reservations.findByReservationRequestId(event.reservationRequestId())).isPresent();
	}

	@Test
	void conflictingEventIdReuseIsRejectedBeforeAnotherFactIsWritten() {
		var account = account("100.00", "75.00");
		accounts.save(account);
		var event = request(account, "reservation-002", "25.00", "fingerprint-001");
		service.handle(event);

		var conflict = request(account, "reservation-002", "30.00", "fingerprint-002");

		assertThatThrownBy(() -> service.handle(conflict)).isInstanceOf(ReservationEventConflictException.class);
		assertThat(outbox.events()).hasSize(1);
	}

	private static ReservationRequestedEvent request(
			Account account, String requestId, String amount, String fingerprint) {
		return new ReservationRequestedEvent(
				UUID.fromString("10000000-0000-4000-8000-000000000001"),
				"AccountReservationRequested.v1", "1.0.0", "transaction-service", NOW,
				requestId, "correlation-001", "causation-001",
				UUID.fromString("40000000-0000-4000-8000-000000000001"), requestId,
				account.id(), AccountId.newId(), new BigDecimal(amount), "AED", NOW.plusSeconds(300), fingerprint);
	}

	private static Account account(String current, String available) {
		return new Account(AccountId.newId(), CustomerId.newId(), "100000000001", null, AccountType.CURRENT,
				"AED", AccountStatus.ACTIVE, new BigDecimal(current), new BigDecimal(available), "open-001", 0,
				NOW, NOW, null);
	}

	private static final class InMemoryAccountRepository implements AccountRepository {
		private final List<Account> values = new ArrayList<>();
		@Override public Account save(Account account) { values.removeIf(value -> value.id().equals(account.id())); values.add(account); return account; }
		@Override public Optional<Account> findById(AccountId id) { return values.stream().filter(value -> value.id().equals(id)).findFirst(); }
		@Override public List<Account> findByCustomerId(CustomerId id) { return List.of(); }
		@Override public com.digitalbank.accountservice.application.port.out.AccountSearchResult search(
				com.digitalbank.accountservice.application.port.out.AccountSearchCriteria criteria) {
			return new com.digitalbank.accountservice.application.port.out.AccountSearchResult(List.of(), criteria.pageNumber(), criteria.pageSize(), 0, 0, true);
		}
	}

	private static final class InMemoryReservationRepository implements AccountReservationRepository {
		private final List<com.digitalbank.accountservice.application.port.in.ReservationView> values = new ArrayList<>();
		@Override public Optional<com.digitalbank.accountservice.application.port.in.ReservationView> findByReservationRequestId(String id) { return values.stream().filter(value -> value.reservationRequestId().equals(id)).findFirst(); }
		@Override public com.digitalbank.accountservice.application.port.in.ReservationView save(
				com.digitalbank.accountservice.application.port.in.ReserveFundsCommand command, Account account, Instant now) {
			var value = new com.digitalbank.accountservice.application.port.in.ReservationView(UUID.randomUUID(), account.id(), command.reservationRequestId(), command.currency(), command.amount(), command.correlationId(), command.causationId(), com.digitalbank.accountservice.domain.model.ReservationStatus.ACTIVE, command.expiresAt(), 0, now, now);
			values.add(value);
			return value;
		}
		@Override public com.digitalbank.accountservice.application.port.in.ReservationView save(com.digitalbank.accountservice.application.port.in.ReservationView value) { values.removeIf(existing -> existing.reservationId().equals(value.reservationId())); values.add(value); return value; }
	}

	private static final class InMemoryCommandInbox implements ReservationCommandInboxRepository {
		private final List<com.digitalbank.accountservice.application.port.in.ReservationInboxEventView> values = new ArrayList<>();
		@Override public Optional<com.digitalbank.accountservice.application.port.in.ReservationInboxEventView> findByEventId(UUID id) { return values.stream().filter(value -> value.eventId().equals(id)).findFirst(); }
		@Override public Optional<com.digitalbank.accountservice.application.port.in.ReservationInboxEventView> findByReservationRequestId(String id) { return values.stream().filter(value -> value.reservationRequestId().equals(id)).findFirst(); }
		@Override public com.digitalbank.accountservice.application.port.in.ReservationInboxEventView save(ReservationRequestedEvent event, Instant processedAt) {
			var value = new com.digitalbank.accountservice.application.port.in.ReservationInboxEventView(event.eventId(), event.eventType(), event.payloadFingerprint(), event.reservationRequestId(), event.transactionId(), event.correlationId(), event.causationId(), UUID.randomUUID(), processedAt);
			values.add(value);
			return value;
		}
		@Override public com.digitalbank.accountservice.application.port.in.ReservationInboxEventView save(com.digitalbank.accountservice.application.port.in.ReservationReleaseRequestedEvent event, Instant processedAt) { return null; }
	}

	private static final class RecordingOutbox implements AccountReservationEventOutbox {
		private final List<com.digitalbank.accountservice.application.port.out.AccountReservationEvent> values = new ArrayList<>();
		@Override public boolean recordIfAbsent(com.digitalbank.accountservice.application.port.out.AccountReservationEvent event) { values.add(event); return true; }
		List<com.digitalbank.accountservice.application.port.out.AccountReservationEvent> events() { return values; }
	}
}
