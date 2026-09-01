package com.digitalbank.accountservice.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.domain.model.Account;

@Repository
class PostgresAccountReservationRepository implements AccountReservationRepository {

	private final SpringDataAccountReservationRepository repository;

	PostgresAccountReservationRepository(SpringDataAccountReservationRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<ReservationView> findByReservationRequestId(String reservationRequestId) {
		return repository.findByReservationRequestId(reservationRequestId).map(AccountReservationJpaMapper::toView);
	}

	@Override
	public List<ReservationView> findExpiredActiveForUpdate(Instant expiresBy, int limit) {
		return repository.findExpiredActiveForUpdate(expiresBy, limit).stream()
				.map(AccountReservationJpaMapper::toView)
				.toList();
	}

	@Override
	public ReservationView save(ReserveFundsCommand command, Account account, Instant now) {
		return AccountReservationJpaMapper.toView(
				repository.saveAndFlush(AccountReservationJpaMapper.toEntity(command, account, now)));
	}

	@Override
	public ReservationView save(ReservationView reservation) {
		return AccountReservationJpaMapper.toView(
				repository.saveAndFlush(AccountReservationJpaMapper.toEntity(reservation)));
	}
}
