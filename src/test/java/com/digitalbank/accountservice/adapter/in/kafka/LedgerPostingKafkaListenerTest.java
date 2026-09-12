package com.digitalbank.accountservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeInputPort;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeResult;
import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.application.port.out.AccountSearchCriteria;
import com.digitalbank.accountservice.application.port.out.AccountSearchResult;
import com.digitalbank.accountservice.application.service.LedgerPostingEventMapper;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class LedgerPostingKafkaListenerTest {

    @Test
    void delegatesACompletedKafkaRecordToTheExistingOutcomePort() {
        var event = completedEvent();
        var record = new ConsumerRecord<>("ledger.posting.completed.v1", 0, 0L, "aggregate-001", "{}");
        var command = new LedgerPostingOutcomeCommand(
                UUID.randomUUID().toString(), "posting-001", "reservation-001", LedgerPostingOutcome.COMPLETED, null);
        var parser = new CapturingParser(event);
        var mapper = new CapturingMapper(command);
        var outcomePort = new CapturingOutcomePort();
        var listener = new LedgerPostingKafkaListener(parser, mapper, outcomePort);

        listener.consumeCompleted(record);

        assertThat(parser.payload).isEqualTo("{}");
        assertThat(parser.expectedEventType).isEqualTo("LedgerPostingCompleted.v1");
        assertThat(mapper.event).isSameAs(event);
        assertThat(outcomePort.command).isEqualTo(command);
    }

    @Test
    void consumesSerializedReversalThroughRealParserAndMapper() {
        var accountId = UUID.fromString("f5aa4b14-6616-4c84-b0d5-3f178cb50864");
        var reversalPostingId = UUID.fromString("72da40ad-55e6-4be0-a7d5-9046dd3e331c");
        var originalPostingId = UUID.fromString("83c5bb71-f59a-4f4c-8a6d-b89cbb6a8bb8");
        var transactionId = UUID.fromString("0e5d3f5b-f9b1-4b8e-9cb2-df7f4dc6d6f3");
        var eventId = UUID.fromString("a1bbbc71-60e6-4a15-b020-f227c54eb80e");
        var occurredAt = Instant.parse("2026-08-31T00:00:00Z");
        var account = Account.open(
                new AccountId(accountId),
                CustomerId.newId(),
                "100000000001",
                null,
                AccountType.CURRENT,
                "USD",
                "open-request-001",
                occurredAt);
        var reservation = new ReservationView(
                UUID.fromString("6a1f7d4d-841a-46aa-9f9f-e8a1e2fc430a"),
                account.id(),
                "reservation-58e271cd",
                "USD",
                new BigDecimal("125.5000"),
                "transfer-58e271cd",
                "command-63ca8eb6",
                ReservationStatus.ACTIVE,
                occurredAt.plusSeconds(900),
                0L,
                occurredAt,
                occurredAt,
                null,
                null,
                null,
                transactionId,
                null);
        var record = new ConsumerRecord<>(
                "ledger.posting.completed.v1",
                0,
                0L,
                "aggregate-001",
                reversalPayload(eventId, occurredAt, reversalPostingId, originalPostingId, accountId, transactionId));
        record.headers().add("event-id", eventId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add("correlation-id", "transfer-58e271cd".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add("causation-id", "command-63ca8eb6".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add("producer", "ledger-service".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add("schema-version", "1.0.0".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add("occurred-at", occurredAt.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var outcomePort = new CapturingOutcomePort();
        var listener = new LedgerPostingKafkaListener(
                new LedgerPostingEventParser(),
                new LedgerPostingEventMapper(
                        new FixedReservationRepository(reservation), new FixedAccountRepository(account)),
                outcomePort);

        listener.consumeCompleted(record);

        assertThat(outcomePort.command)
                .isEqualTo(new LedgerPostingOutcomeCommand(
                        eventId.toString(),
                        reversalPostingId.toString(),
                        reservation.reservationRequestId(),
                        LedgerPostingOutcome.REVERSED,
                        originalPostingId.toString()));
    }

    private static String reversalPayload(
            UUID eventId,
            Instant occurredAt,
            UUID reversalPostingId,
            UUID originalPostingId,
            UUID reservedAccountId,
            UUID transactionId) {
        return """
				{
				  "eventId": "%s",
				  "eventType": "LedgerPostingCompleted.v1",
				  "schemaVersion": "1.0.0",
				  "producer": "ledger-service",
				  "occurredAt": "%s",
				  "aggregateId": "%s",
				  "correlationId": "transfer-58e271cd",
				  "causationId": "command-63ca8eb6",
				  "transactionId": "%s",
				  "reservationRequestId": "reservation-58e271cd",
				  "postingId": "%s",
				  "postingRequestId": "reversal-request-001",
				  "reversalOfLedgerEntryId": "%s",
				  "currency": "USD",
				  "lines": [
				    { "accountId": "68bc664d-75f7-46b7-82bf-659e733eb653", "lineType": "DEBIT", "amount": "125.5000" },
				    { "accountId": "%s", "lineType": "CREDIT", "amount": "125.5000" }
				  ]
				}
				""".formatted(
                        eventId,
                        occurredAt,
                        reversalPostingId,
                        transactionId,
                        reversalPostingId,
                        originalPostingId,
                        reservedAccountId);
    }

    private static GovernedLedgerPostingEvent.Completed completedEvent() {
        var postingId = UUID.fromString("32ef46b9-480c-4fc3-b769-fcc5d1d6d262");
        return new GovernedLedgerPostingEvent.Completed(
                new GovernedLedgerPostingEvent.Metadata(
                        UUID.fromString("5250517d-c99b-42a3-9388-b106a49a2d3b"),
                        "transfer-001",
                        "command-001",
                        "ledger-service",
                        "1.0.0",
                        Instant.parse("2026-08-31T00:00:00Z")),
                postingId,
                "transfer-001",
                "reservation-001",
                postingId,
                "posting-request-001",
                null,
                "USD",
                List.of(
                        new GovernedLedgerPostingEvent.Line(
                                UUID.fromString("3286f9d5-3b44-4d2d-b9bb-0cfc15b395bb"), "DEBIT", "10.0000"),
                        new GovernedLedgerPostingEvent.Line(
                                UUID.fromString("09dcd396-e702-4d6a-836c-3869c808bf2c"), "CREDIT", "10.0000")));
    }

    private static final class CapturingParser extends LedgerPostingEventParser {

        private final GovernedLedgerPostingEvent event;
        private String payload;
        private String expectedEventType;

        private CapturingParser(GovernedLedgerPostingEvent event) {
            this.event = event;
        }

        @Override
        public GovernedLedgerPostingEvent parse(String payload, Map<String, String> headers, String expectedEventType) {
            this.payload = payload;
            this.expectedEventType = expectedEventType;
            return event;
        }
    }

    private static final class CapturingMapper extends LedgerPostingEventMapper {

        private final LedgerPostingOutcomeCommand command;
        private GovernedLedgerPostingEvent event;

        private CapturingMapper(LedgerPostingOutcomeCommand command) {
            super(null, null);
            this.command = command;
        }

        @Override
        public LedgerPostingOutcomeCommand toCommand(GovernedLedgerPostingEvent event) {
            this.event = event;
            return command;
        }
    }

    private static final class CapturingOutcomePort implements LedgerPostingOutcomeInputPort {

        private LedgerPostingOutcomeCommand command;

        @Override
        public LedgerPostingOutcomeResult handle(LedgerPostingOutcomeCommand command) {
            this.command = command;
            return null;
        }
    }

    private static final class FixedReservationRepository implements AccountReservationRepository {

        private final ReservationView reservation;

        private FixedReservationRepository(ReservationView reservation) {
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

    private static final class FixedAccountRepository implements AccountRepository {

        private final Account account;

        private FixedAccountRepository(Account account) {
            this.account = account;
        }

        @Override
        public Account save(Account account) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Account> findById(AccountId accountId) {
            return account.id().equals(accountId) ? Optional.of(account) : Optional.empty();
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
