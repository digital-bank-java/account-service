package com.digitalbank.accountservice.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.digitalbank.accountservice.application.port.in.InboxEventView;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeInputPort;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeResult;
import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.out.AccountInboxEventRepository;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.domain.exception.InsufficientCurrentBalanceException;
import com.digitalbank.accountservice.domain.exception.LedgerPostingOutcomeConflictException;
import com.digitalbank.accountservice.domain.exception.OptimisticLockConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationNotFoundException;
import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.ReservationStatus;

@Service
public class AccountLedgerOutcomeService implements LedgerPostingOutcomeInputPort {

	private final AccountRepository accountRepository;
	private final AccountReservationRepository reservationRepository;
	private final AccountInboxEventRepository inboxEventRepository;
	private final Clock clock;
	private final TransactionOperations transactionOperations;

	@Autowired
	public AccountLedgerOutcomeService(
			AccountRepository accountRepository,
			AccountReservationRepository reservationRepository,
			AccountInboxEventRepository inboxEventRepository,
			Clock clock,
			PlatformTransactionManager transactionManager) {
		this(accountRepository, reservationRepository, inboxEventRepository, clock,
				new TransactionTemplate(transactionManager));
	}

	public AccountLedgerOutcomeService(
			AccountRepository accountRepository,
			AccountReservationRepository reservationRepository,
			AccountInboxEventRepository inboxEventRepository,
			Clock clock) {
		this(accountRepository, reservationRepository, inboxEventRepository, clock, (TransactionOperations) null);
	}

	private AccountLedgerOutcomeService(
			AccountRepository accountRepository,
			AccountReservationRepository reservationRepository,
			AccountInboxEventRepository inboxEventRepository,
			Clock clock,
			TransactionOperations transactionOperations) {
		this.accountRepository = accountRepository;
		this.reservationRepository = reservationRepository;
		this.inboxEventRepository = inboxEventRepository;
		this.clock = clock;
		this.transactionOperations = transactionOperations;
	}

	@Override
	public LedgerPostingOutcomeResult handle(LedgerPostingOutcomeCommand command) {
		try {
			return executeInTransaction(() -> handleWithinTransaction(command));
		} catch (OptimisticLockConflictException | DataIntegrityViolationException exception) {
			return replayAfterConcurrentFailure(command, exception);
		}
	}

	private LedgerPostingOutcomeResult handleWithinTransaction(LedgerPostingOutcomeCommand command) {
		var processedEvent = inboxEventRepository.findByEventId(command.eventId());
		if (processedEvent.isPresent()) {
			return replayEvent(processedEvent.orElseThrow(), command);
		}

		var eventForPosting = inboxEventRepository.findByLedgerPostingId(command.ledgerPostingId());
		if (eventForPosting.isPresent()) {
			return replayPosting(eventForPosting.orElseThrow(), command);
		}

		var reservation = reservationRepository.findByReservationRequestId(command.reservationRequestId())
				.orElseThrow(() -> new ReservationNotFoundException(command.reservationRequestId()));
		var account = accountRepository.findById(reservation.accountId())
				.orElseThrow(() -> new IllegalStateException("Account for reservation is missing"));
		var now = clock.instant();
		var updated = transition(command, reservation, account, now);

		try {
			accountRepository.save(updated.account());
			reservationRepository.save(updated.reservation());
			inboxEventRepository.save(command, now);
		} catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException exception) {
			throw new OptimisticLockConflictException(exception);
		}

		return new LedgerPostingOutcomeResult(
				command.eventId(), command.reservationRequestId(), updated.reservation().status(), false);
	}

	private LedgerPostingOutcomeResult replayAfterConcurrentFailure(
			LedgerPostingOutcomeCommand command,
			RuntimeException failure) {
		var processedEvent = inboxEventRepository.findByEventId(command.eventId());
		if (processedEvent.isPresent()) {
			return replayEvent(processedEvent.orElseThrow(), command);
		}
		var eventForPosting = inboxEventRepository.findByLedgerPostingId(command.ledgerPostingId());
		if (eventForPosting.isPresent()) {
			return replayPosting(eventForPosting.orElseThrow(), command);
		}
		throw failure;
	}

	private LedgerPostingOutcomeResult replayEvent(InboxEventView event, LedgerPostingOutcomeCommand command) {
		if (!event.matches(command)) {
			throw new LedgerPostingOutcomeConflictException("Event id already contains a different ledger outcome");
		}
		return resultForReplay(command, event.reservationRequestId());
	}

	private LedgerPostingOutcomeResult replayPosting(InboxEventView event, LedgerPostingOutcomeCommand command) {
		if (!event.ledgerPostingId().equals(command.ledgerPostingId())
				|| !event.reservationRequestId().equals(command.reservationRequestId())
				|| event.outcome() != command.outcome()
				|| !java.util.Objects.equals(event.originalPostingId(), command.originalPostingId())) {
			throw new LedgerPostingOutcomeConflictException("Ledger posting id already contains a different outcome");
		}
		return resultForReplay(command, event.reservationRequestId());
	}

	private LedgerPostingOutcomeResult resultForReplay(
			LedgerPostingOutcomeCommand command,
			String reservationRequestId) {
		var reservation = reservationRepository.findByReservationRequestId(reservationRequestId)
				.orElseThrow(() -> new ReservationNotFoundException(reservationRequestId));
		return new LedgerPostingOutcomeResult(command.eventId(), reservationRequestId, reservation.status(), true);
	}

	private static Transition transition(
			LedgerPostingOutcomeCommand command,
			ReservationView reservation,
			Account account,
			Instant now) {
		return switch (command.outcome()) {
			case COMPLETED -> commit(command, reservation, account, now);
			case FAILED -> release(command, reservation, account, now);
			case REVERSED -> reverse(command, reservation, account, now);
		};
	}

	private static Transition commit(
			LedgerPostingOutcomeCommand command,
			ReservationView reservation,
			Account account,
			Instant now) {
		if (reservation.status() != ReservationStatus.ACTIVE) {
			if (reservation.status() == ReservationStatus.COMMITTED
					&& command.ledgerPostingId().equals(reservation.ledgerPostingId())) {
				return new Transition(account, reservation);
			}
			throw new ReservationStateConflictException(reservation.reservationRequestId(), reservation.status());
		}
		if (account.currentBalance().compareTo(reservation.amount()) < 0) {
			throw new InsufficientCurrentBalanceException(account.id(), reservation.amount());
		}
		return new Transition(
				withBalances(account, account.currentBalance().subtract(reservation.amount()), account.availableBalance(), now),
				withReservation(reservation, ReservationStatus.COMMITTED, command.ledgerPostingId(), null, now));
	}

	private static Transition release(
			LedgerPostingOutcomeCommand command,
			ReservationView reservation,
			Account account,
			Instant now) {
		if (reservation.status() != ReservationStatus.ACTIVE) {
			if (reservation.status() == ReservationStatus.RELEASED
					&& command.ledgerPostingId().equals(reservation.ledgerPostingId())) {
				return new Transition(account, reservation);
			}
			throw new ReservationStateConflictException(reservation.reservationRequestId(), reservation.status());
		}
		return new Transition(
				withBalances(account, account.currentBalance(), account.availableBalance().add(reservation.amount()), now),
				withReservation(reservation, ReservationStatus.RELEASED, command.ledgerPostingId(), null, now));
	}

	private static Transition reverse(
			LedgerPostingOutcomeCommand command,
			ReservationView reservation,
			Account account,
			Instant now) {
		if (reservation.status() == ReservationStatus.REVERSED
				&& command.ledgerPostingId().equals(reservation.reversedByLedgerPostingId())
				&& command.originalPostingId().equals(reservation.ledgerPostingId())) {
			return new Transition(account, reservation);
		}
		if (reservation.status() != ReservationStatus.COMMITTED
				|| !command.originalPostingId().equals(reservation.ledgerPostingId())) {
			throw new ReservationStateConflictException(reservation.reservationRequestId(), reservation.status());
		}
		return new Transition(
				withBalances(account, account.currentBalance().add(reservation.amount()),
						account.availableBalance().add(reservation.amount()), now),
				withReservation(reservation, ReservationStatus.REVERSED, reservation.ledgerPostingId(), command.ledgerPostingId(), now));
	}

	private static Account withBalances(
			Account account,
			BigDecimal currentBalance,
			BigDecimal availableBalance,
			Instant now) {
		return new Account(
				account.id(), account.customerId(), account.accountNumber(), account.iban(), account.type(), account.currency(),
				account.status(), currentBalance, availableBalance, account.openingRequestId(), account.version(),
				account.createdAt(), now, account.closedAt());
	}

	private static ReservationView withReservation(
			ReservationView reservation,
			ReservationStatus status,
			String ledgerPostingId,
			String reversedByLedgerPostingId,
			Instant now) {
		return new ReservationView(
				reservation.reservationId(), reservation.accountId(), reservation.reservationRequestId(), reservation.currency(),
				reservation.amount(), reservation.correlationId(), reservation.causationId(), status, reservation.expiresAt(),
				reservation.version(), reservation.createdAt(), now, ledgerPostingId, reversedByLedgerPostingId);
	}

	private <T> T executeInTransaction(Supplier<T> operation) {
		if (transactionOperations == null) {
			return operation.get();
		}
		return transactionOperations.execute(status -> operation.get());
	}

	private record Transition(Account account, ReservationView reservation) {
	}
}
