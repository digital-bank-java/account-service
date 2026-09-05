package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.AccountId;
import java.time.Instant;
import java.util.Objects;

public record InboxEventView(
        String eventId,
        String ledgerPostingId,
        String reservationRequestId,
        LedgerPostingOutcome outcome,
        String originalPostingId,
        AccountId destinationAccountId,
        Instant processedAt) {

    public InboxEventView(
            String eventId,
            String ledgerPostingId,
            String reservationRequestId,
            LedgerPostingOutcome outcome,
            String originalPostingId,
            Instant processedAt) {
        this(eventId, ledgerPostingId, reservationRequestId, outcome, originalPostingId, null, processedAt);
    }

    public boolean matches(LedgerPostingOutcomeCommand command) {
        return eventId.equals(command.eventId())
                && ledgerPostingId.equals(command.ledgerPostingId())
                && reservationRequestId.equals(command.reservationRequestId())
                && outcome == command.outcome()
                && Objects.equals(originalPostingId, command.originalPostingId())
                && Objects.equals(destinationAccountId, command.destinationAccountId());
    }
}
