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

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReservationAttempt;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.application.port.in.ReserveFundsInputPort;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.domain.exception.AccountCurrencyMismatchException;
import com.digitalbank.accountservice.domain.exception.AccountNotFoundException;
import com.digitalbank.accountservice.domain.exception.AccountStatusConflictException;
import com.digitalbank.accountservice.domain.exception.InsufficientAvailableBalanceException;
import com.digitalbank.accountservice.domain.exception.OptimisticLockConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationRequestConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationNotFoundException;
import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.util.UUID;

@Service
public class AccountReservationService implements ReserveFundsInputPort {

	private final AccountRepository accountRepository;
	private final AccountReservationRepository reservationRepository;
	private final Clock clock;
	private final TransactionOperations transactionOperations;

	@Autowired
	public AccountReservationService(
			AccountRepository accountRepository,
			AccountReservationRepository reservationRepository,
			Clock clock,
			PlatformTransactionManager transactionManager) {
		this(accountRepository, reservationRepository, clock, new TransactionTemplate(transactionManager));
	}

	public AccountReservationService(
			AccountRepository accountRepository,
			AccountReservationRepository reservationRepository,
			Clock clock) {
		this(accountRepository, reservationRepository, clock, (TransactionOperations) null);
	}

	private AccountReservationService(
			AccountRepository accountRepository,
			AccountReservationRepository reservationRepository,
			Clock clock,
			TransactionOperations transactionOperations) {
		this.accountRepository = accountRepository;
		this.reservationRepository = reservationRepository;
		this.clock = clock;
		this.transactionOperations = transactionOperations;
	}

	@Override
	public ReservationView reserve(ReserveFundsCommand command) {
		try {
			return executeInTransaction(() -> reserveWithinTransaction(command));
		} catch (OptimisticLockConflictException | InsufficientAvailableBalanceException
				| DataIntegrityViolationException exception) {
			return replayAfterConcurrentFailure(command, exception);
		}
	}

	public ReservationAttempt reserveForTransport(ReserveFundsCommand command) {
		return executeInTransaction(() -> {
			try {
				return ReservationAttempt.accepted(reserveWithinTransaction(command));
			} catch (AccountNotFoundException exception) {
				return ReservationAttempt.rejected("ACCOUNT_NOT_FOUND", exception.getMessage());
			} catch (AccountStatusConflictException exception) {
				return ReservationAttempt.rejected("ACCOUNT_NOT_ACTIVE", exception.getMessage());
			} catch (AccountCurrencyMismatchException exception) {
				return ReservationAttempt.rejected("CURRENCY_MISMATCH", exception.getMessage());
			} catch (InsufficientAvailableBalanceException exception) {
				return ReservationAttempt.rejected("INSUFFICIENT_AVAILABLE_BALANCE", exception.getMessage());
			} catch (ReservationRequestConflictException exception) {
				return ReservationAttempt.rejected("CONFLICT", exception.getMessage());
			} catch (IllegalArgumentException exception) {
				return ReservationAttempt.rejected("VALIDATION_ERROR", exception.getMessage());
			}
		});
	}

	public ReservationView release(AccountId accountId, UUID reservationId, String reservationRequestId, Instant now) {
		return executeInTransaction(() -> {
			var reservation = reservationRepository.findByReservationRequestId(reservationRequestId)
					.orElseThrow(() -> new ReservationNotFoundException(reservationRequestId));
			if (!reservation.reservationId().equals(reservationId) || !reservation.accountId().equals(accountId)) {
				throw new ReservationStateConflictException(reservationRequestId, reservation.status());
			}
			if (reservation.status() == ReservationStatus.RELEASED || reservation.status() == ReservationStatus.EXPIRED) {
				return reservation;
			}
			if (reservation.status() != ReservationStatus.ACTIVE) {
				throw new ReservationStateConflictException(reservationRequestId, reservation.status());
			}
			var account = accountRepository.findById(accountId)
					.orElseThrow(() -> new AccountNotFoundException(accountId));
			var updatedAccount = withRestoredAvailableBalance(account, reservation.amount(), now);
			try {
				accountRepository.save(updatedAccount);
				return reservationRepository.save(new ReservationView(
						reservation.reservationId(), reservation.accountId(), reservation.reservationRequestId(),
						reservation.currency(), reservation.amount(), reservation.correlationId(), reservation.causationId(),
						ReservationStatus.RELEASED, reservation.expiresAt(), reservation.version(), reservation.createdAt(),
						now, reservation.ledgerPostingId(), reservation.reversedByLedgerPostingId(),
						reservation.destinationAccountId(), reservation.transactionId(), reservation.acceptedEventId()));
			} catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException exception) {
				throw new OptimisticLockConflictException(exception);
			}
		});
	}

	private ReservationView reserveWithinTransaction(ReserveFundsCommand command) {
		var existingReservation = reservationRepository
				.findByReservationRequestId(command.reservationRequestId());
		if (existingReservation.isPresent()) {
			return replayOrReject(existingReservation.orElseThrow(), command);
		}

		var account = accountRepository.findById(command.accountId())
				.orElseThrow(() -> new AccountNotFoundException(command.accountId()));
		validateAccount(account, command);

		var now = clock.instant();
		if (!command.expiresAt().isAfter(now)) {
			throw new IllegalArgumentException("Reservation expiry timestamp must be in the future");
		}
		var updatedAccount = withReservedAvailableBalance(account, command.amount(), now);
		try {
			var persistedAccount = accountRepository.save(updatedAccount);
			return reservationRepository.save(command, persistedAccount, now);
		} catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException exception) {
			throw new OptimisticLockConflictException(exception);
		}
	}

	private ReservationView replayAfterConcurrentFailure(ReserveFundsCommand command, RuntimeException failure) {
		var existingReservation = reservationRepository.findByReservationRequestId(command.reservationRequestId());
		if (existingReservation.isPresent()) {
			return replayOrReject(existingReservation.orElseThrow(), command);
		}
		throw failure;
	}

	private <T> T executeInTransaction(Supplier<T> operation) {
		if (transactionOperations == null) {
			return operation.get();
		}
		return transactionOperations.execute(status -> operation.get());
	}

	private static ReservationView replayOrReject(ReservationView existing, ReserveFundsCommand command) {
		if (existing.accountId().equals(command.accountId())
				&& (command.destinationAccountId() == null || java.util.Objects.equals(existing.destinationAccountId(), command.destinationAccountId()))
				&& (command.transactionId() == null || java.util.Objects.equals(existing.transactionId(), command.transactionId()))
				&& existing.currency().equals(command.currency())
				&& existing.amount().compareTo(command.amount()) == 0
				&& existing.correlationId().equals(command.correlationId())
				&& existing.causationId().equals(command.causationId())
				&& existing.expiresAt().equals(command.expiresAt())) {
			return existing;
		}
		throw new ReservationRequestConflictException(command.reservationRequestId());
	}

	private static Account withRestoredAvailableBalance(Account account, BigDecimal amount, Instant now) {
		return new Account(account.id(), account.customerId(), account.accountNumber(), account.iban(), account.type(),
				account.currency(), account.status(), account.currentBalance(), account.availableBalance().add(amount),
				account.openingRequestId(), account.version(), account.createdAt(), now, account.closedAt());
	}

	private static void validateAccount(Account account, ReserveFundsCommand command) {
		account.requireActiveForMonetaryOperation("reserve funds");
		if (!account.currency().equals(command.currency())) {
			throw new AccountCurrencyMismatchException(command.accountId(), command.currency());
		}
		if (account.availableBalance().compareTo(command.amount()) < 0) {
			throw new InsufficientAvailableBalanceException(command.accountId(), command.amount());
		}
	}

	private static Account withReservedAvailableBalance(Account account, BigDecimal amount, java.time.Instant now) {
		return new Account(
				account.id(),
				account.customerId(),
				account.accountNumber(),
				account.iban(),
				account.type(),
				account.currency(),
				account.status(),
				account.currentBalance(),
				account.availableBalance().subtract(amount),
				account.openingRequestId(),
				account.version(),
				account.createdAt(),
				now,
				account.closedAt());
	}
}
