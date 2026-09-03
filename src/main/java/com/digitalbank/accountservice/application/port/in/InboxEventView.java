package com.digitalbank.accountservice.application.port.in;

import java.time.Instant;

public record InboxEventView(
        String eventId,
        String ledgerPostingId,
        String reservationRequestId,
        LedgerPostingOutcome outcome,
        String originalPostingId,
        Instant processedAt) {

    public boolean matches(LedgerPostingOutcomeCommand command) {
        return eventId.equals(command.eventId())
                && ledgerPostingId.equals(command.ledgerPostingId())
                && reservationRequestId.equals(command.reservationRequestId())
                && outcome == command.outcome()
                && java.util.Objects.equals(originalPostingId, command.originalPostingId());
    }
}
