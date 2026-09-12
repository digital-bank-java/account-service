package com.digitalbank.accountservice.application.service;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.domain.exception.InvalidLedgerPostingEventException;
import com.digitalbank.accountservice.domain.exception.ReservationNotFoundException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LedgerPostingEventMapper {

    private static final Pattern POSITIVE_DECIMAL_AMOUNT = Pattern.compile("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?$");

    private final AccountReservationRepository reservationRepository;
    private final AccountRepository accountRepository;

    public LedgerPostingEventMapper(
            AccountReservationRepository reservationRepository, AccountRepository accountRepository) {
        this.reservationRepository = reservationRepository;
        this.accountRepository = accountRepository;
    }

    public LedgerPostingOutcomeCommand toCommand(GovernedLedgerPostingEvent event) {
        var reservation = reservationRepository
                .findByReservationRequestId(event.reservationRequestId())
                .orElseThrow(() -> new ReservationNotFoundException(event.reservationRequestId()));
        validateIdentity(event, reservation);
        var account = accountRepository
                .findById(reservation.accountId())
                .orElseThrow(() -> new IllegalStateException("Account for reservation is missing"));

        return switch (event) {
            case GovernedLedgerPostingEvent.Completed completed -> completedCommand(completed, reservation, account);
            case GovernedLedgerPostingEvent.Failed failed ->
                new LedgerPostingOutcomeCommand(
                        failed.metadata().eventId().toString(),
                        failed.postingRequestId(),
                        failed.reservationRequestId(),
                        LedgerPostingOutcome.FAILED,
                        null);
        };
    }

    private LedgerPostingOutcomeCommand completedCommand(
            GovernedLedgerPostingEvent.Completed completed,
            ReservationView reservation,
            com.digitalbank.accountservice.domain.model.Account account) {
        if (!completed.currency().equals(reservation.currency())
                || !completed.currency().equals(account.currency())) {
            throw new InvalidLedgerPostingEventException(
                    "Completed posting currency must match the reservation account");
        }
        completed.lines().forEach(line -> {
            if (line.amount() == null
                    || !POSITIVE_DECIMAL_AMOUNT.matcher(line.amount()).matches()
                    || new java.math.BigDecimal(line.amount()).signum() <= 0) {
                throw new InvalidLedgerPostingEventException(
                        "Ledger line amount must be a positive decimal string with at most four fractional digits");
            }
        });
        var isReversal = completed.reversalOfLedgerEntryId() != null;
        var reservedAccountLineType = isReversal ? "CREDIT" : "DEBIT";
        var matchingReservedAccountLines = completed.lines().stream()
                .filter(line -> line.accountId().equals(reservation.accountId().value()))
                .filter(line -> reservedAccountLineType.equals(line.lineType()))
                .filter(line -> new java.math.BigDecimal(line.amount()).compareTo(reservation.amount()) == 0)
                .count();
        if (matchingReservedAccountLines != 1) {
            throw new InvalidLedgerPostingEventException(
                    isReversal
                            ? "Reversal completed posting must contain one credit line matching the reservation account and amount"
                            : "Completed posting must contain one debit line matching the reservation account and amount");
        }
        var destinationAccountId = reservation.destinationAccountId();
        if (destinationAccountId == null && completed.lines().size() != 2) {
            throw new InvalidLedgerPostingEventException(
                    "Completed posting without a destination reservation must contain exactly one debit and one credit line");
        }
        if (destinationAccountId == null
                && completed.lines().stream()
                                .filter(line -> line.accountId()
                                        .equals(reservation.accountId().value()))
                                .count()
                        != 1) {
            throw new InvalidLedgerPostingEventException(
                    "Completed posting without a destination reservation must not contain two lines for the reserved account");
        }
        if (destinationAccountId != null) {
            var destinationLineType = isReversal ? "DEBIT" : "CREDIT";
            var matchingDestinationLines = completed.lines().stream()
                    .filter(line -> line.accountId().equals(destinationAccountId.value()))
                    .filter(line -> destinationLineType.equals(line.lineType()))
                    .filter(line -> new java.math.BigDecimal(line.amount()).compareTo(reservation.amount()) == 0)
                    .count();
            if (destinationAccountId.equals(reservation.accountId()) || matchingDestinationLines != 1) {
                throw new InvalidLedgerPostingEventException(
                        isReversal
                                ? "Reversal completed posting must contain one debit line matching the destination account and amount"
                                : "Completed posting must contain one credit line matching the destination account and amount");
            }
            var destinationAccount = accountRepository
                    .findById(destinationAccountId)
                    .orElseThrow(() ->
                            new InvalidLedgerPostingEventException("Destination account for reservation is missing"));
            if (!destinationAccount.currency().equals(reservation.currency())) {
                throw new InvalidLedgerPostingEventException("Destination account currency must match the reservation");
            }
        }
        return new LedgerPostingOutcomeCommand(
                completed.metadata().eventId().toString(),
                completed.postingId().toString(),
                completed.reservationRequestId(),
                isReversal ? LedgerPostingOutcome.REVERSED : LedgerPostingOutcome.COMPLETED,
                isReversal ? completed.reversalOfLedgerEntryId().toString() : null,
                destinationAccountId);
    }

    private static void validateIdentity(GovernedLedgerPostingEvent event, ReservationView reservation) {
        if (!Objects.equals(event.reservationRequestId(), reservation.reservationRequestId())) {
            throw new InvalidLedgerPostingEventException(
                    "Ledger event reservation request does not match the reservation");
        }
        if (!Objects.equals(event.metadata().correlationId(), reservation.correlationId())) {
            throw new InvalidLedgerPostingEventException("Ledger event correlation does not match the reservation");
        }
        if (reservation.transactionId() != null) {
            var transactionId = transactionId(event);
            if (transactionId == null || transactionId.isBlank()) {
                throw new InvalidLedgerPostingEventException("Ledger event transaction id is required");
            }
            UUID eventTransactionId;
            try {
                eventTransactionId = UUID.fromString(transactionId);
            } catch (IllegalArgumentException exception) {
                throw new InvalidLedgerPostingEventException("Ledger event transaction id is invalid");
            }
            if (!reservation.transactionId().equals(eventTransactionId)) {
                throw new InvalidLedgerPostingEventException("Ledger event transaction does not match the reservation");
            }
        }
        var storedPostingId = reservation.ledgerPostingId();
        switch (event) {
            case GovernedLedgerPostingEvent.Completed completed -> {
                var expectedPostingId = completed.reversalOfLedgerEntryId() != null
                        ? completed.reversalOfLedgerEntryId().toString()
                        : completed.postingId().toString();
                if (storedPostingId != null && !storedPostingId.equals(expectedPostingId)) {
                    throw new InvalidLedgerPostingEventException("Ledger posting does not match the reservation");
                }
            }
            case GovernedLedgerPostingEvent.Failed failed -> {
                if (storedPostingId != null && !storedPostingId.equals(failed.postingRequestId())) {
                    throw new InvalidLedgerPostingEventException(
                            "Failed ledger posting does not match the reservation");
                }
            }
        }
    }

    private static String transactionId(GovernedLedgerPostingEvent event) {
        return switch (event) {
            case GovernedLedgerPostingEvent.Completed completed -> completed.transactionId();
            case GovernedLedgerPostingEvent.Failed failed -> failed.transactionId();
        };
    }
}
