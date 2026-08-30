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

import org.junit.jupiter.api.Test;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountSearchCriteria;
import com.digitalbank.accountservice.application.port.out.AccountSearchResult;
import com.digitalbank.accountservice.domain.exception.AccountNotFoundException;
import com.digitalbank.accountservice.domain.exception.AccountCurrencyMismatchException;
import com.digitalbank.accountservice.domain.exception.InsufficientAvailableBalanceException;
import com.digitalbank.accountservice.domain.exception.OptimisticLockConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationRequestConflictException;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;

class AccountReservationServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T10:15:30Z");
	private static final Instant EXPIRY = NOW.plusSeconds(900);

	private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
	private final InMemoryAccountReservationRepository reservationRepository = new InMemoryAccountReservationRepository();
	private final AccountReservationService service = new AccountReservationService(
			accountRepository,
			reservationRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void reservesFundsWhenAvailableBalanceIsSufficient() {
		var account = accountWithAvailableBalance("100.0000");

		var reservation = service.reserve(command(account, "reserve-001", "25.00"));

		assertThat(reservation.accountId()).isEqualTo(account.id());
		assertThat(reservation.reservationRequestId()).isEqualTo("reserve-001");
		assertThat(reservation.amount()).isEqualByComparingTo("25.00");
		assertThat(reservation.currency()).isEqualTo("AED");
		assertThat(reservation.correlationId()).isEqualTo("correlation-001");
		assertThat(reservation.causationId()).isEqualTo("causation-001");
		assertThat(reservation.expiresAt()).isEqualTo(EXPIRY);
		assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
		assertThat(reservation.version()).isZero();
		assertThat(accountRepository.findById(account.id()).orElseThrow().availableBalance())
				.isEqualByComparingTo("75.0000");
		assertThat(accountRepository.findById(account.id()).orElseThrow().version()).isEqualTo(1L);
		assertThat(reservationRepository.savedReservations()).containsExactly(reservation);
	}

	@Test
	void rejectsReservationWhenAvailableBalanceIsInsufficient() {
		var account = accountWithAvailableBalance("10.00");

		assertThatThrownBy(() -> service.reserve(command(account, "reserve-002", "10.01")))
				.isInstanceOf(InsufficientAvailableBalanceException.class);
		assertThat(reservationRepository.savedReservations()).isEmpty();
		assertThat(accountRepository.findById(account.id()).orElseThrow().availableBalance())
				.isEqualByComparingTo("10.00");
	}

	@Test
	void rejectsReservationWhenCurrencyDoesNotMatchAccount() {
		var account = accountWithAvailableBalance("100.00");
		var command = new ReserveFundsCommand(
				"reserve-currency-mismatch",
				account.id(),
				"USD",
				new BigDecimal("10.00"),
				"correlation-001",
				"causation-001",
				EXPIRY);

		assertThatThrownBy(() -> service.reserve(command))
				.isInstanceOf(AccountCurrencyMismatchException.class);
		assertThat(reservationRepository.savedReservations()).isEmpty();
	}

	@Test
	void rejectsReservationForUnknownAccount() {
		var accountId = AccountId.newId();

		assertThatThrownBy(() -> service.reserve(command(accountId, "reserve-003", "10.00")))
				.isInstanceOf(AccountNotFoundException.class);
		assertThat(reservationRepository.savedReservations()).isEmpty();
	}

	@Test
	void replaysExactDuplicateReservation() {
		var account = accountWithAvailableBalance("100.00");
		var command = command(account, "reserve-004", "25.00");
		var original = reservationRepository.save(command, account, NOW);

		var replay = service.reserve(command);

		assertThat(replay).isEqualTo(original);
		assertThat(accountRepository.findById(account.id()).orElseThrow().availableBalance())
				.isEqualByComparingTo("100.00");
		assertThat(reservationRepository.savedReservations()).containsExactly(original);
	}

	@Test
	void rejectsDuplicateReservationWithChangedPayload() {
		var account = accountWithAvailableBalance("100.00");
		var command = command(account, "reserve-005", "25.00");
		reservationRepository.save(command, account, NOW);

		assertThatThrownBy(() -> service.reserve(command(account, "reserve-005", "30.00")))
				.isInstanceOf(ReservationRequestConflictException.class);
		assertThat(accountRepository.findById(account.id()).orElseThrow().availableBalance())
				.isEqualByComparingTo("100.00");
	}

	@Test
	void translatesOptimisticLockFailure() {
		var account = accountWithAvailableBalance("100.00");
		accountRepository.failNextSaveWithOptimisticLock();

		assertThatThrownBy(() -> service.reserve(command(account, "reserve-006", "25.00")))
				.isInstanceOf(OptimisticLockConflictException.class);
		assertThat(reservationRepository.savedReservations()).isEmpty();
	}

	private Account accountWithAvailableBalance(String availableBalance) {
		var account = new Account(
				AccountId.newId(),
				CustomerId.newId(),
				"1000000001",
				null,
				AccountType.CURRENT,
				"AED",
				AccountStatus.ACTIVE,
				new BigDecimal(availableBalance),
				new BigDecimal(availableBalance),
				"open-request-001",
				0L,
				NOW,
				NOW,
				null);
		accountRepository.save(account);
		return account;
	}

	private static ReserveFundsCommand command(Account account, String requestId, String amount) {
		return command(account.id(), requestId, amount);
	}

	private static ReserveFundsCommand command(AccountId accountId, String requestId, String amount) {
		return new ReserveFundsCommand(
				requestId,
				accountId,
				"AED",
				new BigDecimal(amount),
				"correlation-001",
				"causation-001",
				EXPIRY);
	}

	private static final class InMemoryAccountRepository implements AccountRepository {

		private final List<Account> accounts = new ArrayList<>();
		private boolean failNextSave;

		@Override
		public Account save(Account account) {
			if (failNextSave) {
				failNextSave = false;
				throw new jakarta.persistence.OptimisticLockException("stale account");
			}
			var priorAccount = accounts.stream()
					.filter(candidate -> candidate.id().equals(account.id()))
					.findFirst();
			var version = priorAccount.isEmpty() ? account.version() : account.version() + 1;
			var saved = new Account(
					account.id(),
					account.customerId(),
					account.accountNumber(),
					account.iban(),
					account.type(),
					account.currency(),
					account.status(),
					account.currentBalance(),
					account.availableBalance(),
					account.openingRequestId(),
					version,
					account.createdAt(),
					account.updatedAt(),
					account.closedAt());
			accounts.removeIf(existing -> existing.id().equals(account.id()));
			accounts.add(saved);
			return saved;
		}

		@Override
		public Optional<Account> findById(AccountId accountId) {
			return accounts.stream().filter(account -> account.id().equals(accountId)).findFirst();
		}

		@Override
		public List<Account> findByCustomerId(CustomerId customerId) {
			return accounts.stream().filter(account -> account.customerId().equals(customerId)).toList();
		}

		@Override
		public AccountSearchResult search(AccountSearchCriteria criteria) {
			return new AccountSearchResult(List.of(), criteria.pageNumber(), criteria.pageSize(), 0, 0, true);
		}

		void failNextSaveWithOptimisticLock() {
			failNextSave = true;
		}
	}

	private static final class InMemoryAccountReservationRepository implements AccountReservationRepository {

		private final List<ReservationView> reservations = new ArrayList<>();

		@Override
		public Optional<ReservationView> findByReservationRequestId(String reservationRequestId) {
			return reservations.stream()
					.filter(reservation -> reservation.reservationRequestId().equals(reservationRequestId))
					.findFirst();
		}

		@Override
		public ReservationView save(ReserveFundsCommand command, Account account, Instant now) {
			var reservation = new ReservationView(
					java.util.UUID.randomUUID(),
					account.id(),
					command.reservationRequestId(),
					command.currency(),
					command.amount(),
					command.correlationId(),
					command.causationId(),
					ReservationStatus.ACTIVE,
					command.expiresAt(),
					0L,
					now,
					now);
			reservations.add(reservation);
			return reservation;
		}

		@Override
		public ReservationView save(ReservationView reservation) {
			reservations.removeIf(existing -> existing.reservationId().equals(reservation.reservationId()));
			reservations.add(reservation);
			return reservation;
		}

		List<ReservationView> savedReservations() {
			return List.copyOf(reservations);
		}
	}
}
