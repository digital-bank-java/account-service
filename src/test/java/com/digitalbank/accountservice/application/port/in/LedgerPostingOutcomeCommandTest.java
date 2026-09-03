package com.digitalbank.accountservice.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class LedgerPostingOutcomeCommandTest {

    @Test
    void trimsAndAcceptsACompletedOutcome() {
        var command = new LedgerPostingOutcomeCommand(
                " event-001 ", " posting-001 ", " reservation-001 ", LedgerPostingOutcome.COMPLETED, null);

        assertThat(command.eventId()).isEqualTo("event-001");
        assertThat(command.ledgerPostingId()).isEqualTo("posting-001");
        assertThat(command.reservationRequestId()).isEqualTo("reservation-001");
        assertThat(command.outcome()).isEqualTo(LedgerPostingOutcome.COMPLETED);
        assertThat(command.originalPostingId()).isNull();
    }

    @Test
    void rejectsBlankIdentity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LedgerPostingOutcomeCommand(
                        " ", "posting-001", "reservation-001", LedgerPostingOutcome.COMPLETED, null));
    }

    @Test
    void requiresOriginalPostingForReversal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LedgerPostingOutcomeCommand(
                        "event-001", "reversal-001", "reservation-001", LedgerPostingOutcome.REVERSED, null));
    }

    @Test
    void rejectsOriginalPostingForNonReversal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LedgerPostingOutcomeCommand(
                        "event-001", "posting-001", "reservation-001", LedgerPostingOutcome.FAILED, "posting-000"));
    }
}
