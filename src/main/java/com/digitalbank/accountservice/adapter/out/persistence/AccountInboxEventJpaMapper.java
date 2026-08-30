package com.digitalbank.accountservice.adapter.out.persistence;

import java.time.Instant;

import com.digitalbank.accountservice.application.port.in.InboxEventView;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;

final class AccountInboxEventJpaMapper {

	private AccountInboxEventJpaMapper() {
	}

	static AccountInboxEventJpaEntity toEntity(
			LedgerPostingOutcomeCommand command,
			Instant processedAt) {
		return new AccountInboxEventJpaEntity(
				command.eventId(),
				command.ledgerPostingId(),
				command.reservationRequestId(),
				command.outcome(),
				command.originalPostingId(),
				processedAt);
	}

	static InboxEventView toView(AccountInboxEventJpaEntity entity) {
		return new InboxEventView(
				entity.eventId(),
				entity.ledgerPostingId(),
				entity.reservationRequestId(),
				entity.outcome(),
				entity.originalPostingId(),
				entity.processedAt());
	}
}
