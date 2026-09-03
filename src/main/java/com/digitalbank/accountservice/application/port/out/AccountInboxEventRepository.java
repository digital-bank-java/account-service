package com.digitalbank.accountservice.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.digitalbank.accountservice.application.port.in.InboxEventView;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;

public interface AccountInboxEventRepository {

	Optional<InboxEventView> findByEventId(String eventId);

	Optional<InboxEventView> findByLedgerPostingId(String ledgerPostingId);

	InboxEventView save(LedgerPostingOutcomeCommand command, Instant processedAt);
}
