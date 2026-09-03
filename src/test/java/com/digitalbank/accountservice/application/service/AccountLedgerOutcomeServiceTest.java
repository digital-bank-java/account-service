package com.digitalbank.accountservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.accountservice.application.port.in.InboxEventView;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeResult;
import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.application.port.out.AccountInboxEventRepository;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationEvent;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventOutbox;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.application.port.out.AccountSearchCriteria;
import com.digitalbank.accountservice.application.port.out.AccountSearchResult;
import com.digitalbank.accountservice.domain.exception.AccountStatusConflictException;
import com.digitalbank.accountservice.domain.exception.LedgerPostingOutcomeConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationExpiredException;
import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AccountLedgerOutcomeServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:15:30Z");

    private final List<String> saveOrder = new ArrayList<>();
    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository(saveOrder);
    private final InMemoryAccountReservationRepository reservationRepository =
            new InMemoryAccountReservationRepository(saveOrder);
    private final InMemoryAccountInboxEventRepository inboxRepository =
            new InMemoryAccountInboxEventRepository(saveOrder);
    private final AccountLedgerOutcomeService service = new AccountLedgerOutcomeService(
            accountRepository, reservationRepository, inboxRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void commitsActiveReservationAndDecreasesCurrentBalance() {
        var fixture = activeReservation("reservation-complete");

        var result = service.handle(command(
                "event-complete",
                "posting-complete",
                fixture.reservationRequestId(),
                LedgerPostingOutcome.COMPLETED,
                null));

        assertThat(result)
                .isEqualTo(new LedgerPostingOutcomeResult(
                        "event-complete", fixture.reservationRequestId(), ReservationStatus.COMMITTED, false));
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().currentBalance())
                .isEqualByComparingTo("75.00");
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().availableBalance())
                .isEqualByComparingTo("75.00");
        assertThat(reservationRepository
                        .findByReservationRequestId(fixture.reservationRequestId())
                        .orElseThrow())
                .satisfies(saved -> {
                    assertThat(saved.status()).isEqualTo(ReservationStatus.COMMITTED);
                    assertThat(saved.ledgerPostingId()).isEqualTo("posting-complete");
                });
    }

    @Test
    void releasesActiveReservationAndRestoresAvailableBalanceOnFailure() {
        var fixture = activeReservation("reservation-failed");

        var result = service.handle(command(
                "event-failed", "posting-failed", fixture.reservationRequestId(), LedgerPostingOutcome.FAILED, null));

        assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().currentBalance())
                .isEqualByComparingTo("100.00");
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().availableBalance())
                .isEqualByComparingTo("100.00");
        assertThat(reservationRepository
                        .findByReservationRequestId(fixture.reservationRequestId())
                        .orElseThrow()
                        .status())
                .isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void preservesReservationTransportMetadataWhenLedgerFailurePublishesReleaseFact() {
        var fixture = activeReservation("reservation-failed-transport");
        var destinationAccountId = AccountId.newId();
        var transactionId = UUID.randomUUID();
        var acceptedEventId = UUID.randomUUID();
        var transportReservation = new ReservationView(
                fixture.reservationId(),
                fixture.accountId(),
                fixture.reservationRequestId(),
                fixture.currency(),
                fixture.amount(),
                fixture.correlationId(),
                fixture.causationId(),
                fixture.status(),
                fixture.expiresAt(),
                fixture.version(),
                fixture.createdAt(),
                fixture.updatedAt(),
                fixture.ledgerPostingId(),
                fixture.reversedByLedgerPostingId(),
                destinationAccountId,
                transactionId,
                acceptedEventId);
        reservationRepository.save(transportReservation);
        var events = new ArrayList<AccountReservationEvent>();
        AccountReservationEventOutbox outbox = event -> {
            events.add(event);
            return true;
        };
        var transportService = new AccountLedgerOutcomeService(
                accountRepository,
                reservationRepository,
                inboxRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                noOpTransactionManager(),
                outbox);

        transportService.handle(command(
                "event-failed-transport",
                "posting-failed-transport",
                transportReservation.reservationRequestId(),
                LedgerPostingOutcome.FAILED,
                null));

        var saved = reservationRepository
                .findByReservationRequestId(transportReservation.reservationRequestId())
                .orElseThrow();
        assertThat(saved.destinationAccountId()).isEqualTo(destinationAccountId);
        assertThat(saved.transactionId()).isEqualTo(transactionId);
        assertThat(saved.acceptedEventId()).isEqualTo(acceptedEventId);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.transactionId()).isEqualTo(transactionId);
            assertThat(event.destinationAccountId()).isEqualTo(destinationAccountId.value());
        });
    }

    private static PlatformTransactionManager noOpTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
    }

    @Test
    void reversesCommittedReservationAndRestoresBothBalances() {
        var fixture = committedReservation("reservation-reverse");

        var result = service.handle(command(
                "event-reversed",
                "posting-reversal",
                fixture.reservationRequestId(),
                LedgerPostingOutcome.REVERSED,
                "posting-original"));

        assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.REVERSED);
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().currentBalance())
                .isEqualByComparingTo("100.00");
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().availableBalance())
                .isEqualByComparingTo("100.00");
        assertThat(reservationRepository
                        .findByReservationRequestId(fixture.reservationRequestId())
                        .orElseThrow())
                .satisfies(saved -> {
                    assertThat(saved.status()).isEqualTo(ReservationStatus.REVERSED);
                    assertThat(saved.ledgerPostingId()).isEqualTo("posting-original");
                    assertThat(saved.reversedByLedgerPostingId()).isEqualTo("posting-reversal");
                });
    }

    @Test
    void replaysDuplicateEventWithoutApplyingTheTransitionTwice() {
        var fixture = activeReservation("reservation-duplicate");
        var command = command(
                "event-duplicate",
                "posting-duplicate",
                fixture.reservationRequestId(),
                LedgerPostingOutcome.COMPLETED,
                null);

        service.handle(command);
        var replay = service.handle(command);

        assertThat(replay.duplicate()).isTrue();
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().currentBalance())
                .isEqualByComparingTo("75.00");
        assertThat(inboxRepository.events()).hasSize(1);
    }

    @Test
    void replaysEquivalentPostingCorrelationWithAnotherEventId() {
        var fixture = activeReservation("reservation-correlation");
        service.handle(command(
                "event-correlation-001",
                "posting-correlation",
                fixture.reservationRequestId(),
                LedgerPostingOutcome.COMPLETED,
                null));

        var replay = service.handle(command(
                "event-correlation-002",
                "posting-correlation",
                fixture.reservationRequestId(),
                LedgerPostingOutcome.COMPLETED,
                null));

        assertThat(replay.duplicate()).isTrue();
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().currentBalance())
                .isEqualByComparingTo("75.00");
        assertThat(inboxRepository.events()).hasSize(1);
    }

    @Test
    void rejectsConflictingPayloadForProcessedEvent() {
        var fixture = activeReservation("reservation-conflict");
        service.handle(command(
                "event-conflict",
                "posting-conflict",
                fixture.reservationRequestId(),
                LedgerPostingOutcome.COMPLETED,
                null));

        assertThatThrownBy(() -> service.handle(command(
                        "event-conflict",
                        "posting-conflict-2",
                        fixture.reservationRequestId(),
                        LedgerPostingOutcome.COMPLETED,
                        null)))
                .isInstanceOf(LedgerPostingOutcomeConflictException.class);
    }

    @Test
    void rejectsReversalBeforeSuccessfulCompletion() {
        var fixture = activeReservation("reservation-out-of-order");

        assertThatThrownBy(() -> service.handle(command(
                        "event-out-of-order",
                        "posting-reversal",
                        fixture.reservationRequestId(),
                        LedgerPostingOutcome.REVERSED,
                        "posting-original")))
                .isInstanceOf(ReservationStateConflictException.class);
        assertThat(accountRepository.findById(fixture.accountId()).orElseThrow().availableBalance())
                .isEqualByComparingTo("75.00");
    }

    @Test
    void rejectsFreshMonetaryOutcomesForSuspendedAndClosedAccounts() {
        for (var accountStatus : List.of(AccountStatus.SUSPENDED, AccountStatus.CLOSED)) {
            for (var outcome : LedgerPostingOutcome.values()) {
                var fixture =
                        reservationForOutcome("reservation-" + accountStatus + "-" + outcome, accountStatus, outcome);
                var beforeAccount =
                        accountRepository.findById(fixture.accountId()).orElseThrow();

                assertThatThrownBy(() -> service.handle(command(
                                "event-" + accountStatus + "-" + outcome,
                                "posting-" + accountStatus + "-" + outcome,
                                fixture.reservationRequestId(),
                                outcome,
                                outcome == LedgerPostingOutcome.REVERSED ? "posting-original" : null)))
                        .isInstanceOf(AccountStatusConflictException.class);
                assertThat(accountRepository.findById(fixture.accountId())).contains(beforeAccount);
                assertThat(reservationRepository.findByReservationRequestId(fixture.reservationRequestId()))
                        .contains(fixture);
            }
        }
        assertThat(inboxRepository.events()).isEmpty();
    }

    @Test
    void rejectsCompletedOutcomeAtTheReservationExpiryBoundaryWithoutDebiting() {
        var account = accountRepository.save(account(100, 75));
        var reservation =
                reservation(account, "reservation-expired-completion", ReservationStatus.ACTIVE, null, null, NOW);
        reservationRepository.save(reservation);

        assertThatThrownBy(() -> service.handle(command(
                        "event-expired-completion",
                        "posting-expired-completion",
                        reservation.reservationRequestId(),
                        LedgerPostingOutcome.COMPLETED,
                        null)))
                .isInstanceOf(ReservationExpiredException.class);
        assertThat(accountRepository.findById(account.id()).orElseThrow().currentBalance())
                .isEqualByComparingTo("100.00");
        assertThat(reservationRepository.findByReservationRequestId(reservation.reservationRequestId()))
                .contains(reservation);
        assertThat(inboxRepository.events()).isEmpty();
    }

    @Test
    void rejectsCompletedOutcomeForSweptExpiredReservationWithoutDebiting() {
        var account = accountRepository.save(account(100, 100));
        var reservation = reservation(
                account, "reservation-swept-expiry", ReservationStatus.EXPIRED, null, null, NOW.minusSeconds(1));
        reservationRepository.save(reservation);

        assertThatThrownBy(() -> service.handle(command(
                        "event-swept-expiry",
                        "posting-swept-expiry",
                        reservation.reservationRequestId(),
                        LedgerPostingOutcome.COMPLETED,
                        null)))
                .isInstanceOf(ReservationExpiredException.class);
        assertThat(accountRepository.findById(account.id()).orElseThrow().currentBalance())
                .isEqualByComparingTo("100.00");
        assertThat(inboxRepository.events()).isEmpty();
    }

    @Test
    void persistsReservationBeforeAccountToMatchExpiryLockOrder() {
        var fixture = activeReservation("reservation-lock-order");
        saveOrder.clear();

        service.handle(command(
                "event-lock-order",
                "posting-lock-order",
                fixture.reservationRequestId(),
                LedgerPostingOutcome.COMPLETED,
                null));

        assertThat(saveOrder).containsExactly("reservation", "account", "inbox");
    }

    private ReservationView activeReservation(String requestId) {
        var account = accountRepository.save(account(100, 75));
        var reservation = reservation(account, requestId, ReservationStatus.ACTIVE, null, null);
        reservationRepository.save(reservation);
        return reservation;
    }

    private ReservationView committedReservation(String requestId) {
        var account = accountRepository.save(account(75, 75));
        var reservation = reservation(account, requestId, ReservationStatus.COMMITTED, "posting-original", null);
        reservationRepository.save(reservation);
        return reservation;
    }

    private ReservationView reservationForOutcome(
            String requestId, AccountStatus accountStatus, LedgerPostingOutcome outcome) {
        var committed = outcome == LedgerPostingOutcome.REVERSED;
        var account = accountRepository.save(account(committed ? 75 : 100, 75, accountStatus));
        var reservation = reservation(
                account,
                requestId,
                committed ? ReservationStatus.COMMITTED : ReservationStatus.ACTIVE,
                committed ? "posting-original" : null,
                null);
        reservationRepository.save(reservation);
        return reservation;
    }

    private static Account account(int currentBalance, int availableBalance) {
        return account(currentBalance, availableBalance, AccountStatus.ACTIVE);
    }

    private static Account account(int currentBalance, int availableBalance, AccountStatus status) {
        return new Account(
                AccountId.newId(),
                CustomerId.newId(),
                "1000000001" + currentBalance + availableBalance,
                null,
                AccountType.CURRENT,
                "AED",
                status,
                new BigDecimal(currentBalance),
                new BigDecimal(availableBalance),
                "open-request-" + currentBalance + availableBalance,
                0L,
                NOW,
                NOW,
                status == AccountStatus.CLOSED ? NOW : null);
    }

    private static ReservationView reservation(
            Account account,
            String requestId,
            ReservationStatus status,
            String ledgerPostingId,
            String reversedByLedgerPostingId) {
        return reservation(
                account, requestId, status, ledgerPostingId, reversedByLedgerPostingId, NOW.plusSeconds(900));
    }

    private static ReservationView reservation(
            Account account,
            String requestId,
            ReservationStatus status,
            String ledgerPostingId,
            String reversedByLedgerPostingId,
            Instant expiresAt) {
        return new ReservationView(
                java.util.UUID.randomUUID(),
                account.id(),
                requestId,
                "AED",
                new BigDecimal("25.00"),
                "correlation-" + requestId,
                "causation-" + requestId,
                status,
                expiresAt,
                0L,
                NOW,
                NOW,
                ledgerPostingId,
                reversedByLedgerPostingId);
    }

    private static LedgerPostingOutcomeCommand command(
            String eventId,
            String ledgerPostingId,
            String reservationRequestId,
            LedgerPostingOutcome outcome,
            String originalPostingId) {
        return new LedgerPostingOutcomeCommand(
                eventId, ledgerPostingId, reservationRequestId, outcome, originalPostingId);
    }

    private static final class InMemoryAccountRepository implements AccountRepository {

        private final List<Account> accounts = new ArrayList<>();
        private final List<String> saveOrder;

        private InMemoryAccountRepository(List<String> saveOrder) {
            this.saveOrder = saveOrder;
        }

        @Override
        public Account save(Account account) {
            saveOrder.add("account");
            var existing = accounts.stream()
                    .filter(candidate -> candidate.id().equals(account.id()))
                    .findFirst();
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
                    existing.map(value -> value.version() + 1).orElse(account.version()),
                    account.createdAt(),
                    account.updatedAt(),
                    account.closedAt());
            accounts.removeIf(candidate -> candidate.id().equals(account.id()));
            accounts.add(saved);
            return saved;
        }

        @Override
        public Optional<Account> findById(AccountId accountId) {
            return accounts.stream()
                    .filter(account -> account.id().equals(accountId))
                    .findFirst();
        }

        @Override
        public List<Account> findByCustomerId(CustomerId customerId) {
            return List.of();
        }

        @Override
        public AccountSearchResult search(AccountSearchCriteria criteria) {
            return new AccountSearchResult(List.of(), criteria.pageNumber(), criteria.pageSize(), 0, 0, true);
        }
    }

    private static final class InMemoryAccountReservationRepository implements AccountReservationRepository {

        private final List<ReservationView> reservations = new ArrayList<>();
        private final List<String> saveOrder;

        private InMemoryAccountReservationRepository(List<String> saveOrder) {
            this.saveOrder = saveOrder;
        }

        @Override
        public Optional<ReservationView> findByReservationRequestId(String reservationRequestId) {
            return reservations.stream()
                    .filter(value -> value.reservationRequestId().equals(reservationRequestId))
                    .findFirst();
        }

        @Override
        public ReservationView save(ReserveFundsCommand command, Account account, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReservationView save(ReservationView reservation) {
            saveOrder.add("reservation");
            reservations.removeIf(value -> value.reservationId().equals(reservation.reservationId()));
            reservations.add(reservation);
            return reservation;
        }
    }

    private static final class InMemoryAccountInboxEventRepository implements AccountInboxEventRepository {

        private final List<InboxEventView> events = new ArrayList<>();
        private final List<String> saveOrder;

        private InMemoryAccountInboxEventRepository(List<String> saveOrder) {
            this.saveOrder = saveOrder;
        }

        @Override
        public Optional<InboxEventView> findByEventId(String eventId) {
            return events.stream()
                    .filter(value -> value.eventId().equals(eventId))
                    .findFirst();
        }

        @Override
        public Optional<InboxEventView> findByLedgerPostingId(String ledgerPostingId) {
            return events.stream()
                    .filter(value -> value.ledgerPostingId().equals(ledgerPostingId))
                    .findFirst();
        }

        @Override
        public InboxEventView save(LedgerPostingOutcomeCommand command, Instant processedAt) {
            saveOrder.add("inbox");
            var event = new InboxEventView(
                    command.eventId(),
                    command.ledgerPostingId(),
                    command.reservationRequestId(),
                    command.outcome(),
                    command.originalPostingId(),
                    processedAt);
            events.add(event);
            return event;
        }

        List<InboxEventView> events() {
            return List.copyOf(events);
        }
    }
}
