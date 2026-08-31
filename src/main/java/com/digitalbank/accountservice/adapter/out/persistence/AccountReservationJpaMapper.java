package com.digitalbank.accountservice.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;

final class AccountReservationJpaMapper {

	private AccountReservationJpaMapper() {
	}

	static AccountReservationJpaEntity toEntity(
			ReserveFundsCommand command,
			Account account,
			Instant now) {
		return new AccountReservationJpaEntity(
				UUID.randomUUID(),
				account.id().value(),
				command.reservationRequestId(),
				command.currency(),
				command.amount(),
				command.correlationId(),
				command.causationId(),
				ReservationStatus.ACTIVE,
				command.expiresAt(),
				0L,
				now,
				now,
				null,
				null,
				command.destinationAccountId() == null ? null : command.destinationAccountId().value(),
				command.transactionId(),
				command.acceptedEventId());
	}

	static AccountReservationJpaEntity toEntity(ReservationView reservation) {
		return new AccountReservationJpaEntity(
				reservation.reservationId(),
				reservation.accountId().value(),
				reservation.reservationRequestId(),
				reservation.currency(),
				reservation.amount(),
				reservation.correlationId(),
				reservation.causationId(),
				reservation.status(),
				reservation.expiresAt(),
				reservation.version(),
				reservation.createdAt(),
				reservation.updatedAt(),
				reservation.ledgerPostingId(),
				reservation.reversedByLedgerPostingId(),
				reservation.destinationAccountId() == null ? null : reservation.destinationAccountId().value(),
				reservation.transactionId(),
				reservation.acceptedEventId());
	}

	static ReservationView toView(AccountReservationJpaEntity entity) {
		return new ReservationView(
				entity.id(),
				new AccountId(entity.accountId()),
				entity.reservationRequestId(),
				entity.currency(),
				entity.amount(),
				entity.correlationId(),
				entity.causationId(),
				entity.status(),
				entity.expiresAt(),
				entity.version(),
				entity.createdAt(),
				entity.updatedAt(),
				entity.ledgerPostingId(),
				entity.reversedByLedgerPostingId(),
				entity.destinationAccountId() == null ? null : new AccountId(entity.destinationAccountId()),
				entity.transactionId(),
				entity.acceptedEventId());
	}
}
