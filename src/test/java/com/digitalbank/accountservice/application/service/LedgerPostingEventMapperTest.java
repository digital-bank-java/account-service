package com.digitalbank.accountservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.application.port.out.AccountSearchCriteria;
import com.digitalbank.accountservice.application.port.out.AccountSearchResult;
import com.digitalbank.accountservice.domain.exception.InvalidLedgerPostingEventException;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LedgerPostingEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final Account ACCOUNT = new Account(
            new AccountId(UUID.fromString("f5aa4b14-6616-4c84-b0d5-3f178cb50864")),
            CustomerId.newId(),
            "100000000001",
            null,
            AccountType.CURRENT,
            "USD",
            AccountStatus.ACTIVE,
            new BigDecimal("200.0000"),
            new BigDecimal("75.0000"),
            "open-request-001",
            0L,
            NOW,
            NOW,
            null);
    private static final ReservationView RESERVATION = new ReservationView(
            UUID.randomUUID(),
            ACCOUNT.id(),
            "reservation-58e271cd",
            "USD",
            new BigDecimal("125.5000"),
            "transfer-58e271cd",
            "command-63ca8eb6",
            ReservationStatus.ACTIVE,
            NOW.plusSeconds(900),
            0L,
            NOW,
            NOW);

    private final LedgerPostingEventMapper mapper = new LedgerPostingEventMapper(
            new InMemoryReservationRepository(RESERVATION), new InMemoryAccountRepository(ACCOUNT));

    @Test
    void mapsCompletedEventWithMatchingReservedDebitLine() {
        var eventId = UUID.fromString("fbd2e85a-bc91-4dc0-a1b3-9509d0d6c241");
        var postingId = UUID.fromString("dd480939-2dde-49b7-aa22-c8f22a7789ad");

        var command = mapper.toCommand(completedEvent(eventId, postingId, "125.5000"));

        assertThat(command)
                .isEqualTo(new LedgerPostingOutcomeCommand(
                        eventId.toString(),
                        postingId.toString(),
                        RESERVATION.reservationRequestId(),
                        LedgerPostingOutcome.COMPLETED,
                        null));
    }

    @Test
    void mapsReversalCompletedEventWithMatchingReservedCreditLine() {
        var eventId = UUID.fromString("a1bbbc71-60e6-4a15-b020-f227c54eb80e");
        var postingId = UUID.fromString("72da40ad-55e6-4be0-a7d5-9046dd3e331c");
        var originalPostingId = UUID.fromString("83c5bb71-f59a-4f4c-8a6d-b89cbb6a8bb8");
        var command = new AtomicReference<LedgerPostingOutcomeCommand>();

        assertThatCode(() ->
                        command.set(mapper.toCommand(reversalEvent(eventId, postingId, originalPostingId, "CREDIT"))))
                .doesNotThrowAnyException();

        assertThat(command.get())
                .isEqualTo(new LedgerPostingOutcomeCommand(
                        eventId.toString(),
                        postingId.toString(),
                        RESERVATION.reservationRequestId(),
                        LedgerPostingOutcome.REVERSED,
                        originalPostingId.toString()));
    }

    @Test
    void rejectsCompletedEventWithAmountBeyondGovernedScale() {
        assertThatThrownBy(() -> mapper.toCommand(completedEvent(UUID.randomUUID(), UUID.randomUUID(), "125.50000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCompletedEventWhoseDebitDoesNotBelongToTheReservedAccount() {
        var event = completedEvent(UUID.randomUUID(), UUID.randomUUID(), "125.5000");
        var wrongDebitAccount = new GovernedLedgerPostingEvent.Completed(
                event.metadata(),
                event.aggregateId(),
                event.transactionId(),
                event.reservationRequestId(),
                event.postingId(),
                event.postingRequestId(),
                event.reversalOfLedgerEntryId(),
                event.currency(),
                List.of(
                        new GovernedLedgerPostingEvent.Line(UUID.randomUUID(), "DEBIT", "125.5000"),
                        new GovernedLedgerPostingEvent.Line(UUID.randomUUID(), "CREDIT", "125.5000")));

        assertThatThrownBy(() -> mapper.toCommand(wrongDebitAccount))
                .isInstanceOf(InvalidLedgerPostingEventException.class);
    }

    @Test
    void rejectsCompletedEventWhoseCurrencyDoesNotMatchTheReservation() {
        var event = completedEvent(UUID.randomUUID(), UUID.randomUUID(), "125.5000");
        var wrongCurrency = new GovernedLedgerPostingEvent.Completed(
                event.metadata(),
                event.aggregateId(),
                event.transactionId(),
                event.reservationRequestId(),
                event.postingId(),
                event.postingRequestId(),
                event.reversalOfLedgerEntryId(),
                "AED",
                event.lines());

        assertThatThrownBy(() -> mapper.toCommand(wrongCurrency))
                .isInstanceOf(InvalidLedgerPostingEventException.class);
    }

    @Test
    void rejectsCompletedEventWhoseCorrelationDoesNotMatchTheReservation() {
        var event = completedEvent(UUID.randomUUID(), UUID.randomUUID(), "125.5000");
        var wrongCorrelation = new GovernedLedgerPostingEvent.Completed(
                new GovernedLedgerPostingEvent.Metadata(
                        event.metadata().eventId(),
                        "different-correlation",
                        event.metadata().causationId(),
                        event.metadata().producer(),
                        event.metadata().schemaVersion(),
                        event.metadata().occurredAt()),
                event.aggregateId(),
                event.transactionId(),
                event.reservationRequestId(),
                event.postingId(),
                event.postingRequestId(),
                event.reversalOfLedgerEntryId(),
                event.currency(),
                event.lines());

        assertThatThrownBy(() -> mapper.toCommand(wrongCorrelation))
                .isInstanceOf(InvalidLedgerPostingEventException.class);
    }

    @Test
    void rejectsCompletedEventWithMultipleLinesForSourceOnlyReservation() {
        var event = completedEvent(UUID.randomUUID(), UUID.randomUUID(), "125.5000");
        var extraLine = new GovernedLedgerPostingEvent.Completed(
                event.metadata(),
                event.aggregateId(),
                event.transactionId(),
                event.reservationRequestId(),
                event.postingId(),
                event.postingRequestId(),
                event.reversalOfLedgerEntryId(),
                event.currency(),
                List.of(
                        event.lines().get(0),
                        event.lines().get(1),
                        new GovernedLedgerPostingEvent.Line(UUID.randomUUID(), "CREDIT", "10.0000"),
                        new GovernedLedgerPostingEvent.Line(UUID.randomUUID(), "DEBIT", "10.0000")));

        assertThatThrownBy(() -> mapper.toCommand(extraLine))
                .isInstanceOf(InvalidLedgerPostingEventException.class)
                .hasMessageContaining("exactly one debit and one credit");
    }

    @Test
    void rejectsCompletedEventThatCreditsTheReservedAccountWithoutADestinationReservation() {
        var event = completedEvent(UUID.randomUUID(), UUID.randomUUID(), "125.5000");
        var sameAccountCredit = new GovernedLedgerPostingEvent.Completed(
                event.metadata(),
                event.aggregateId(),
                event.transactionId(),
                event.reservationRequestId(),
                event.postingId(),
                event.postingRequestId(),
                event.reversalOfLedgerEntryId(),
                event.currency(),
                List.of(
                        event.lines().get(0),
                        new GovernedLedgerPostingEvent.Line(ACCOUNT.id().value(), "CREDIT", "125.5000")));

        assertThatThrownBy(() -> mapper.toCommand(sameAccountCredit))
                .isInstanceOf(InvalidLedgerPostingEventException.class)
                .hasMessageContaining("two lines for the reserved account");
    }

    @Test
    void rejectsCompletedEventWhoseTransactionDoesNotMatchTheReservation() {
        var transactionId = UUID.randomUUID();
        var reservation = reservationWith(transactionId, null);
        var event = completedEvent(UUID.randomUUID(), UUID.randomUUID(), "125.5000");
        var mapper = mapperFor(reservation);
        var wrongTransaction = new GovernedLedgerPostingEvent.Completed(
                event.metadata(),
                event.aggregateId(),
                UUID.randomUUID().toString(),
                event.reservationRequestId(),
                event.postingId(),
                event.postingRequestId(),
                event.reversalOfLedgerEntryId(),
                event.currency(),
                event.lines());

        assertThatThrownBy(() -> mapper.toCommand(wrongTransaction))
                .isInstanceOf(InvalidLedgerPostingEventException.class)
                .hasMessageContaining("transaction does not match");
    }

    @Test
    void rejectsCompletedEventWhosePostingDoesNotMatchPersistedPostingIdentity() {
        var expectedPostingId = UUID.randomUUID().toString();
        var reservation = reservationWith(null, expectedPostingId);
        var mapper = mapperFor(reservation);
        var event = completedEvent(UUID.randomUUID(), UUID.randomUUID(), "125.5000");

        assertThatThrownBy(() -> mapper.toCommand(event))
                .isInstanceOf(InvalidLedgerPostingEventException.class)
                .hasMessageContaining("posting does not match");
    }

    @Test
    void rejectsReversalCompletedEventWhoseReservedAccountLineIsDebit() {
        assertThatThrownBy(() -> mapper.toCommand(
                        reversalEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DEBIT")))
                .isInstanceOf(InvalidLedgerPostingEventException.class);
    }

    private static GovernedLedgerPostingEvent.Completed completedEvent(UUID eventId, UUID postingId, String amount) {
        return new GovernedLedgerPostingEvent.Completed(
                new GovernedLedgerPostingEvent.Metadata(
                        eventId, "transfer-58e271cd", "command-63ca8eb6", "ledger-service", "1.0.0", NOW),
                postingId,
                "transfer-58e271cd",
                RESERVATION.reservationRequestId(),
                postingId,
                "posting-request-4d93c803",
                null,
                "USD",
                List.of(
                        new GovernedLedgerPostingEvent.Line(ACCOUNT.id().value(), "DEBIT", amount),
                        new GovernedLedgerPostingEvent.Line(UUID.randomUUID(), "CREDIT", amount)));
    }

    private static GovernedLedgerPostingEvent.Completed reversalEvent(
            UUID eventId, UUID postingId, UUID originalPostingId, String reservedAccountLineType) {
        var counterpartyLineType = "DEBIT".equals(reservedAccountLineType) ? "CREDIT" : "DEBIT";
        return new GovernedLedgerPostingEvent.Completed(
                new GovernedLedgerPostingEvent.Metadata(
                        eventId, "transfer-58e271cd", "command-63ca8eb6", "ledger-service", "1.0.0", NOW),
                postingId,
                "transfer-58e271cd",
                RESERVATION.reservationRequestId(),
                postingId,
                "posting-request-4d93c803",
                originalPostingId,
                "USD",
                List.of(
                        new GovernedLedgerPostingEvent.Line(UUID.randomUUID(), counterpartyLineType, "125.5000"),
                        new GovernedLedgerPostingEvent.Line(
                                ACCOUNT.id().value(), reservedAccountLineType, "125.5000")));
    }

    private LedgerPostingEventMapper mapperFor(ReservationView reservation) {
        return new LedgerPostingEventMapper(
                new InMemoryReservationRepository(reservation), new InMemoryAccountRepository(ACCOUNT));
    }

    private static ReservationView reservationWith(UUID transactionId, String ledgerPostingId) {
        return new ReservationView(
                RESERVATION.reservationId(),
                RESERVATION.accountId(),
                RESERVATION.reservationRequestId(),
                RESERVATION.currency(),
                RESERVATION.amount(),
                RESERVATION.correlationId(),
                RESERVATION.causationId(),
                RESERVATION.status(),
                RESERVATION.expiresAt(),
                RESERVATION.version(),
                RESERVATION.createdAt(),
                RESERVATION.updatedAt(),
                ledgerPostingId,
                null,
                RESERVATION.destinationAccountId(),
                transactionId,
                RESERVATION.acceptedEventId());
    }

    private static final class InMemoryReservationRepository implements AccountReservationRepository {

        private final ReservationView reservation;

        private InMemoryReservationRepository(ReservationView reservation) {
            this.reservation = reservation;
        }

        @Override
        public Optional<ReservationView> findByReservationRequestId(String reservationRequestId) {
            return reservation.reservationRequestId().equals(reservationRequestId)
                    ? Optional.of(reservation)
                    : Optional.empty();
        }

        @Override
        public ReservationView save(
                com.digitalbank.accountservice.application.port.in.ReserveFundsCommand command,
                Account account,
                Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReservationView save(ReservationView reservation) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryAccountRepository implements AccountRepository {

        private final Map<AccountId, Account> accounts;

        private InMemoryAccountRepository(Account account) {
            accounts = Map.of(account.id(), account);
        }

        @Override
        public Account save(Account account) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Account> findById(AccountId accountId) {
            return Optional.ofNullable(accounts.get(accountId));
        }

        @Override
        public List<Account> findByCustomerId(CustomerId customerId) {
            return List.of();
        }

        @Override
        public AccountSearchResult search(AccountSearchCriteria criteria) {
            return new AccountSearchResult(List.of(), 0, 0, 0, 0, true);
        }
    }
}
