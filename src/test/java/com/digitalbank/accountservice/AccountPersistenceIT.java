package com.digitalbank.accountservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.application.port.out.AccountInboxEventRepository;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.application.service.AccountLedgerOutcomeService;
import com.digitalbank.accountservice.application.service.AccountReservationExpiryService;
import com.digitalbank.accountservice.application.service.AccountReservationService;
import com.digitalbank.accountservice.application.service.AccountService;
import com.digitalbank.accountservice.application.service.LedgerPostingEventMapper;
import com.digitalbank.accountservice.domain.exception.InsufficientAvailableBalanceException;
import com.digitalbank.accountservice.domain.exception.InvalidLedgerPostingEventException;
import com.digitalbank.accountservice.domain.exception.OptimisticLockConflictException;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class AccountPersistenceIT {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:15:30Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountReservationRepository reservationRepository;

    @Autowired
    private AccountReservationService reservationService;

    @Autowired
    private AccountReservationExpiryService reservationExpiryService;

    @Autowired
    private TestAccountReservationRepository testReservationRepository;

    @Autowired
    private AccountLedgerOutcomeService ledgerOutcomeService;

    @Autowired
    private LedgerPostingEventMapper ledgerPostingEventMapper;

    @Autowired
    private TestAccountInboxEventRepository testInboxEventRepository;

    @Test
    void opensAndLoadsAccount() {
        var customerId = CustomerId.newId();

        var account = accountService.openAccount(
                customerId,
                "100000000001",
                "AE070331234567890123456",
                AccountType.CURRENT,
                "AED",
                "open-account-request-001");

        var savedAccount = accountService.findById(account.id());

        assertThat(savedAccount).hasValueSatisfying(saved -> {
            assertThat(saved.id()).isEqualTo(account.id());
            assertThat(saved.customerId()).isEqualTo(customerId);
            assertThat(saved.accountNumber()).isEqualTo("100000000001");
            assertThat(saved.iban()).isEqualTo("AE070331234567890123456");
            assertThat(saved.type()).isEqualTo(AccountType.CURRENT);
            assertThat(saved.currency()).isEqualTo("AED");
            assertThat(saved.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(saved.currentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.openingRequestId()).isEqualTo("open-account-request-001");
            assertThat(saved.version()).isZero();
            assertThat(saved.createdAt()).isEqualTo(FIXED_NOW);
            assertThat(saved.updatedAt()).isEqualTo(FIXED_NOW);
        });
    }

    @Test
    void findsAccountsByCustomerId() {
        var now = Instant.parse("2026-01-02T10:15:30Z");
        var customerId = CustomerId.newId();
        var currentAccount = Account.open(
                AccountId.newId(),
                customerId,
                "100000000002",
                "AE070331234567890123457",
                AccountType.CURRENT,
                "AED",
                "open-account-request-002",
                now);
        var savingsAccount = Account.open(
                AccountId.newId(),
                customerId,
                "100000000003",
                "AE070331234567890123458",
                AccountType.SAVINGS,
                "AED",
                "open-account-request-003",
                now);

        accountRepository.save(currentAccount);
        accountRepository.save(savingsAccount);

        var accounts = accountRepository.findByCustomerId(customerId);

        assertThat(accounts)
                .extracting(Account::accountNumber)
                .containsExactlyInAnyOrder("100000000002", "100000000003");
    }

    @Test
    void persistsReservationAndDecreasesAvailableBalance() {
        var account = fundedAccount("reservation-persist");
        var command = reservationCommand(account, "reservation-persist-request", "25.00");

        var reservation = reservationService.reserve(command);

        assertThat(reservation).satisfies(saved -> {
            assertThat(saved.reservationId()).isNotNull();
            assertThat(saved.accountId()).isEqualTo(account.id());
            assertThat(saved.reservationRequestId()).isEqualTo(command.reservationRequestId());
            assertThat(saved.amount()).isEqualByComparingTo("25.00");
            assertThat(saved.currency()).isEqualTo("AED");
            assertThat(saved.status().name()).isEqualTo("ACTIVE");
            assertThat(saved.version()).isZero();
            assertThat(saved.createdAt()).isEqualTo(FIXED_NOW);
            assertThat(saved.updatedAt()).isEqualTo(FIXED_NOW);
        });
        assertThat(accountService.findById(account.id())).hasValueSatisfying(saved -> {
            assertThat(saved.currentBalance()).isEqualByComparingTo("100.00");
            assertThat(saved.availableBalance()).isEqualByComparingTo("75.00");
            assertThat(saved.version()).isEqualTo(account.version() + 1);
        });
    }

    @Test
    void enforcesUniqueReservationRequestIdInDatabase() {
        var account = fundedAccount("reservation-unique");
        var command = reservationCommand(account, "reservation-unique-request", "25.00");
        var first = reservationService.reserve(command);

        assertThatThrownBy(() -> reservationRepository.save(command, account, FIXED_NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reservationService.reserve(command)).isEqualTo(first);
    }

    @Test
    void concurrentReservationsCannotOverspendAvailableBalance() throws Exception {
        var account = fundedAccount("reservation-concurrent");
        var ready = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(
                    () -> reserveAfter(ready, reservationCommand(account, "reservation-concurrent-001", "75.00")));
            var second = executor.submit(
                    () -> reserveAfter(ready, reservationCommand(account, "reservation-concurrent-002", "75.00")));
            ready.countDown();

            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(java.util.stream.Stream.of(firstResult, secondResult)
                            .filter(result -> result instanceof ReservationView)
                            .count())
                    .isEqualTo(1);
            assertThat(java.util.stream.Stream.of(firstResult, secondResult)
                            .filter(result -> result instanceof OptimisticLockConflictException
                                    || result instanceof InsufficientAvailableBalanceException)
                            .count())
                    .isEqualTo(1);
        }

        assertThat(accountService.findById(account.id())).hasValueSatisfying(saved -> {
            assertThat(saved.availableBalance()).isEqualByComparingTo("25.00");
            assertThat(saved.availableBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
    }

    @Test
    void concurrentDuplicateReservationsReplayTheCommittedOriginal() throws Exception {
        var account = fundedAccount("reservation-duplicate-concurrent");
        var command = reservationCommand(account, "reservation-duplicate-concurrent-request", "75.00");
        testReservationRepository.coordinateInitialLookups(command.reservationRequestId());
        var ready = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reserveAfter(ready, command));
            var second = executor.submit(() -> reserveAfter(ready, command));
            ready.countDown();

            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult).isInstanceOf(ReservationView.class);
            assertThat(secondResult).isInstanceOf(ReservationView.class);
            assertThat(((ReservationView) firstResult).reservationId())
                    .isEqualTo(((ReservationView) secondResult).reservationId());
        }

        assertThat(accountService.findById(account.id())).hasValueSatisfying(saved -> {
            assertThat(saved.availableBalance()).isEqualByComparingTo("25.00");
            assertThat(saved.version()).isEqualTo(account.version() + 1);
        });
    }

    @Test
    void rollsBackAccountUpdateWhenReservationPersistenceFails() {
        var account = fundedAccount("reservation-rollback");
        var before = accountService.findById(account.id()).orElseThrow();
        testReservationRepository.failNextSave();

        assertThatThrownBy(() -> reservationService.reserve(
                        reservationCommand(account, "reservation-rollback-request", "25.00")))
                .isInstanceOf(TestReservationPersistenceException.class);

        assertThat(accountService.findById(account.id())).hasValueSatisfying(after -> {
            assertThat(after.availableBalance()).isEqualByComparingTo(before.availableBalance());
            assertThat(after.version()).isEqualTo(before.version());
        });
    }

    @Test
    void persistsCompletedOutcomeAndReplaysDuplicateDelivery() {
        var account = fundedAccount("ledger-complete");
        var reservation = reservationService.reserve(reservationCommand(account, "ledger-complete-request", "25.00"));
        var command = new LedgerPostingOutcomeCommand(
                "ledger-complete-event",
                "ledger-complete-posting",
                reservation.reservationRequestId(),
                LedgerPostingOutcome.COMPLETED,
                null);

        var first = ledgerOutcomeService.handle(command);
        var replay = ledgerOutcomeService.handle(command);

        assertThat(first.duplicate()).isFalse();
        assertThat(replay.duplicate()).isTrue();
        assertThat(accountService.findById(account.id())).hasValueSatisfying(saved -> {
            assertThat(saved.currentBalance()).isEqualByComparingTo("75.00");
            assertThat(saved.availableBalance()).isEqualByComparingTo("75.00");
        });
        assertThat(reservationRepository.findByReservationRequestId(reservation.reservationRequestId()))
                .hasValueSatisfying(saved -> {
                    assertThat(saved.status())
                            .isEqualTo(com.digitalbank.accountservice.domain.model.ReservationStatus.COMMITTED);
                    assertThat(saved.ledgerPostingId()).isEqualTo("ledger-complete-posting");
                });
        assertThat(testInboxEventRepository.findByEventId(command.eventId())).isPresent();
    }

    @Test
    void commitsValidGovernedCompletionAndRejectsInvalidCurrencyWithoutMutation() {
        var account = fundedAccount("ledger-kafka-mapper");
        var reservation =
                reservationService.reserve(reservationCommand(account, "ledger-kafka-mapper-request", "25.00"));
        var validEvent = completedEvent(account, reservation, "AED");

        var result = ledgerOutcomeService.handle(ledgerPostingEventMapper.toCommand(validEvent));

        assertThat(result.reservationStatus())
                .isEqualTo(com.digitalbank.accountservice.domain.model.ReservationStatus.COMMITTED);
        assertThat(accountService.findById(account.id()).orElseThrow().currentBalance())
                .isEqualByComparingTo("75.00");

        var invalidAccount = fundedAccount("ledger-kafka-invalid");
        var invalidReservation =
                reservationService.reserve(reservationCommand(invalidAccount, "ledger-kafka-invalid-request", "25.00"));
        var invalidEvent = completedEvent(invalidAccount, invalidReservation, "USD");

        assertThatThrownBy(() -> ledgerPostingEventMapper.toCommand(invalidEvent))
                .isInstanceOf(InvalidLedgerPostingEventException.class);
        assertThat(accountService.findById(invalidAccount.id()).orElseThrow().currentBalance())
                .isEqualByComparingTo("100.00");
        assertThat(reservationRepository
                        .findByReservationRequestId(invalidReservation.reservationRequestId())
                        .orElseThrow()
                        .status())
                .isEqualTo(com.digitalbank.accountservice.domain.model.ReservationStatus.ACTIVE);
        assertThat(testInboxEventRepository.findByEventId(
                        invalidEvent.metadata().eventId().toString()))
                .isEmpty();
    }

    @Test
    void persistsFailureAndAppendOnlyReversalOutcome() {
        var account = fundedAccount("ledger-reversal");
        var reservation = reservationService.reserve(reservationCommand(account, "ledger-reversal-request", "25.00"));
        var completed = new LedgerPostingOutcomeCommand(
                "ledger-reversal-complete-event",
                "ledger-reversal-original",
                reservation.reservationRequestId(),
                LedgerPostingOutcome.COMPLETED,
                null);
        ledgerOutcomeService.handle(completed);

        var reversed = ledgerOutcomeService.handle(new LedgerPostingOutcomeCommand(
                "ledger-reversal-event",
                "ledger-reversal-posting",
                reservation.reservationRequestId(),
                LedgerPostingOutcome.REVERSED,
                "ledger-reversal-original"));

        assertThat(reversed.reservationStatus())
                .isEqualTo(com.digitalbank.accountservice.domain.model.ReservationStatus.REVERSED);
        assertThat(accountService.findById(account.id())).hasValueSatisfying(saved -> {
            assertThat(saved.currentBalance()).isEqualByComparingTo("100.00");
            assertThat(saved.availableBalance()).isEqualByComparingTo("100.00");
        });
        assertThat(testInboxEventRepository.findByEventId("ledger-reversal-complete-event"))
                .isPresent();
        assertThat(testInboxEventRepository.findByEventId("ledger-reversal-event"))
                .isPresent();
    }

    @Test
    void persistsFailedOutcomeAndReleasesReservation() {
        var account = fundedAccount("ledger-failed");
        var reservation = reservationService.reserve(reservationCommand(account, "ledger-failed-request", "25.00"));

        var result = ledgerOutcomeService.handle(new LedgerPostingOutcomeCommand(
                "ledger-failed-event",
                "ledger-failed-posting",
                reservation.reservationRequestId(),
                LedgerPostingOutcome.FAILED,
                null));

        assertThat(result.reservationStatus())
                .isEqualTo(com.digitalbank.accountservice.domain.model.ReservationStatus.RELEASED);
        assertThat(accountService.findById(account.id())).hasValueSatisfying(saved -> {
            assertThat(saved.currentBalance()).isEqualByComparingTo("100.00");
            assertThat(saved.availableBalance()).isEqualByComparingTo("100.00");
        });
        assertThat(reservationRepository.findByReservationRequestId(reservation.reservationRequestId()))
                .hasValueSatisfying(saved -> assertThat(saved.ledgerPostingId()).isEqualTo("ledger-failed-posting"));
        assertThat(testInboxEventRepository.findByEventId("ledger-failed-event"))
                .isPresent();
    }

    @Test
    void expiresPersistedHoldAndRejectsLateCompletionWithoutDebiting() {
        var account = fundedAccount("reservation-expiry");
        var heldAccount = accountRepository.save(new Account(
                account.id(),
                account.customerId(),
                account.accountNumber(),
                account.iban(),
                account.type(),
                account.currency(),
                account.status(),
                account.currentBalance(),
                new BigDecimal("75.00"),
                account.openingRequestId(),
                account.version(),
                account.createdAt(),
                account.updatedAt(),
                account.closedAt()));
        var command = new ReserveFundsCommand(
                "reservation-expiry-request",
                heldAccount.id(),
                "AED",
                new BigDecimal("25.00"),
                "reservation-expiry-correlation",
                "reservation-expiry-causation",
                FIXED_NOW.minusSeconds(1));
        var reservation = reservationRepository.save(command, heldAccount, FIXED_NOW.minusSeconds(900));

        assertThat(reservationExpiryService.expireDueReservations()).isOne();

        assertThat(reservationRepository.findByReservationRequestId(reservation.reservationRequestId()))
                .hasValueSatisfying(saved -> assertThat(saved.status())
                        .isEqualTo(com.digitalbank.accountservice.domain.model.ReservationStatus.EXPIRED));
        assertThat(accountService.findById(account.id()).orElseThrow().availableBalance())
                .isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> ledgerOutcomeService.handle(new LedgerPostingOutcomeCommand(
                        "reservation-expiry-event",
                        "reservation-expiry-posting",
                        reservation.reservationRequestId(),
                        LedgerPostingOutcome.COMPLETED,
                        null)))
                .isInstanceOf(com.digitalbank.accountservice.domain.exception.ReservationExpiredException.class);
        assertThat(accountService.findById(account.id()).orElseThrow().currentBalance())
                .isEqualByComparingTo("100.00");
        assertThat(testInboxEventRepository.findByEventId("reservation-expiry-event"))
                .isEmpty();
    }

    @Test
    void rollsBackAccountAndReservationWhenInboxPersistenceFails() {
        var account = fundedAccount("ledger-inbox-rollback");
        var reservation =
                reservationService.reserve(reservationCommand(account, "ledger-inbox-rollback-request", "25.00"));
        var before = accountService.findById(account.id()).orElseThrow();
        testInboxEventRepository.failNextSave();

        assertThatThrownBy(() -> ledgerOutcomeService.handle(new LedgerPostingOutcomeCommand(
                        "ledger-inbox-rollback-event",
                        "ledger-inbox-rollback-posting",
                        reservation.reservationRequestId(),
                        LedgerPostingOutcome.COMPLETED,
                        null)))
                .isInstanceOf(TestInboxPersistenceException.class);

        assertThat(accountService.findById(account.id())).hasValueSatisfying(after -> {
            assertThat(after.currentBalance()).isEqualByComparingTo(before.currentBalance());
            assertThat(after.availableBalance()).isEqualByComparingTo(before.availableBalance());
        });
        assertThat(reservationRepository.findByReservationRequestId(reservation.reservationRequestId()))
                .hasValueSatisfying(after -> assertThat(after.status())
                        .isEqualTo(com.digitalbank.accountservice.domain.model.ReservationStatus.ACTIVE));
        assertThat(testInboxEventRepository.findByEventId("ledger-inbox-rollback-event"))
                .isEmpty();
    }

    private Object reserveAfter(CountDownLatch ready, ReserveFundsCommand command) {
        try {
            ready.await();
            return reservationService.reserve(command);
        } catch (RuntimeException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        }
    }

    private static GovernedLedgerPostingEvent.Completed completedEvent(
            Account account, ReservationView reservation, String currency) {
        var postingId = UUID.randomUUID();
        return new GovernedLedgerPostingEvent.Completed(
                new GovernedLedgerPostingEvent.Metadata(
                        UUID.randomUUID(),
                        reservation.correlationId(),
                        reservation.causationId(),
                        "ledger-service",
                        "1.0.0",
                        FIXED_NOW),
                postingId,
                reservation.correlationId(),
                reservation.reservationRequestId(),
                postingId,
                "posting-request-" + postingId,
                null,
                currency,
                java.util.List.of(
                        new GovernedLedgerPostingEvent.Line(account.id().value(), "DEBIT", "25.00"),
                        new GovernedLedgerPostingEvent.Line(UUID.randomUUID(), "CREDIT", "25.00")));
    }

    private Account fundedAccount(String label) {
        var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var account = accountService.openAccount(
                CustomerId.newId(), "RES" + suffix, null, AccountType.CURRENT, "AED", "open-" + label + "-" + suffix);
        return accountRepository.save(new Account(
                account.id(),
                account.customerId(),
                account.accountNumber(),
                account.iban(),
                account.type(),
                account.currency(),
                account.status(),
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                account.openingRequestId(),
                account.version(),
                account.createdAt(),
                account.updatedAt(),
                account.closedAt()));
    }

    private static ReserveFundsCommand reservationCommand(Account account, String requestId, String amount) {
        return new ReserveFundsCommand(
                requestId,
                account.id(),
                "AED",
                new BigDecimal(amount),
                "correlation-" + requestId,
                "causation-" + requestId,
                FIXED_NOW.plusSeconds(900));
    }

    @TestConfiguration
    static class ReservationRepositoryTestConfiguration {

        @Bean
        @Primary
        TestAccountReservationRepository testAccountReservationRepository(
                @Qualifier("postgresAccountReservationRepository") AccountReservationRepository delegate) {
            return new TestAccountReservationRepository(delegate);
        }
    }

    @TestConfiguration
    static class InboxRepositoryTestConfiguration {

        @Bean
        @Primary
        TestAccountInboxEventRepository testAccountInboxEventRepository(
                @Qualifier("postgresAccountInboxEventRepository") AccountInboxEventRepository delegate) {
            return new TestAccountInboxEventRepository(delegate);
        }
    }

    private static final class TestAccountReservationRepository implements AccountReservationRepository {

        private final AccountReservationRepository delegate;
        private final AtomicBoolean failNextSave = new AtomicBoolean();
        private final AtomicInteger coordinatedLookupCount = new AtomicInteger();
        private volatile String coordinatedRequestId;
        private volatile CountDownLatch coordinatedLookups;

        private TestAccountReservationRepository(AccountReservationRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.util.Optional<ReservationView> findByReservationRequestId(String reservationRequestId) {
            coordinateLookupIfRequested(reservationRequestId);
            return delegate.findByReservationRequestId(reservationRequestId);
        }

        @Override
        public java.util.List<ReservationView> findExpiredActiveForUpdate(Instant expiresBy, int limit) {
            return delegate.findExpiredActiveForUpdate(expiresBy, limit);
        }

        @Override
        public ReservationView save(ReserveFundsCommand command, Account account, Instant now) {
            if (failNextSave.compareAndSet(true, false)) {
                throw new TestReservationPersistenceException();
            }
            return delegate.save(command, account, now);
        }

        @Override
        public ReservationView save(ReservationView reservation) {
            if (failNextSave.compareAndSet(true, false)) {
                throw new TestReservationPersistenceException();
            }
            return delegate.save(reservation);
        }

        void failNextSave() {
            failNextSave.set(true);
        }

        void coordinateInitialLookups(String requestId) {
            coordinatedRequestId = requestId;
            coordinatedLookupCount.set(0);
            coordinatedLookups = new CountDownLatch(2);
        }

        private void coordinateLookupIfRequested(String requestId) {
            if (!requestId.equals(coordinatedRequestId)) {
                return;
            }
            var lookupNumber = coordinatedLookupCount.getAndIncrement();
            if (lookupNumber >= 2) {
                return;
            }
            var lookups = coordinatedLookups;
            lookups.countDown();
            try {
                if (!lookups.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out coordinating duplicate reservation lookups");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted coordinating duplicate reservation lookups", exception);
            }
        }
    }

    private static final class TestReservationPersistenceException extends RuntimeException {}

    private static final class TestAccountInboxEventRepository implements AccountInboxEventRepository {

        private final AccountInboxEventRepository delegate;
        private final AtomicBoolean failNextSave = new AtomicBoolean();

        private TestAccountInboxEventRepository(AccountInboxEventRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.util.Optional<com.digitalbank.accountservice.application.port.in.InboxEventView> findByEventId(
                String eventId) {
            return delegate.findByEventId(eventId);
        }

        @Override
        public java.util.Optional<com.digitalbank.accountservice.application.port.in.InboxEventView>
                findByLedgerPostingId(String ledgerPostingId) {
            return delegate.findByLedgerPostingId(ledgerPostingId);
        }

        @Override
        public com.digitalbank.accountservice.application.port.in.InboxEventView save(
                LedgerPostingOutcomeCommand command, Instant processedAt) {
            if (failNextSave.compareAndSet(true, false)) {
                throw new TestInboxPersistenceException();
            }
            return delegate.save(command, processedAt);
        }

        void failNextSave() {
            failNextSave.set(true);
        }
    }

    private static final class TestInboxPersistenceException extends RuntimeException {}

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
