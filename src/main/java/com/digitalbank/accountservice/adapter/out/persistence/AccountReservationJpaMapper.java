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
				now);
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
				entity.updatedAt());
	}
}
