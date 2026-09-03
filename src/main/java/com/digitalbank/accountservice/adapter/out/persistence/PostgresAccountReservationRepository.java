package com.digitalbank.accountservice.adapter.out.persistence;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.domain.model.Account;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

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
    public ReservationView save(ReserveFundsCommand command, Account account, Instant now) {
        return AccountReservationJpaMapper.toView(
                repository.saveAndFlush(AccountReservationJpaMapper.toEntity(command, account, now)));
    }
}
