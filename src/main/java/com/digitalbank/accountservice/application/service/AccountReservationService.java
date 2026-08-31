package com.digitalbank.accountservice.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.application.port.in.ReserveFundsInputPort;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.domain.exception.AccountCurrencyMismatchException;
import com.digitalbank.accountservice.domain.exception.AccountNotFoundException;
import com.digitalbank.accountservice.domain.exception.InsufficientAvailableBalanceException;
import com.digitalbank.accountservice.domain.exception.OptimisticLockConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationRequestConflictException;
import com.digitalbank.accountservice.domain.model.Account;

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
				&& existing.currency().equals(command.currency())
				&& existing.amount().compareTo(command.amount()) == 0
				&& existing.correlationId().equals(command.correlationId())
				&& existing.causationId().equals(command.causationId())
				&& existing.expiresAt().equals(command.expiresAt())) {
			return existing;
		}
		throw new ReservationRequestConflictException(command.reservationRequestId());
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
