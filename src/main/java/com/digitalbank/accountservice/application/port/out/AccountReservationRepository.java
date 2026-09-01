package com.digitalbank.accountservice.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.domain.model.Account;

public interface AccountReservationRepository {

	Optional<ReservationView> findByReservationRequestId(String reservationRequestId);

	ReservationView save(ReserveFundsCommand command, Account account, Instant now);

	ReservationView save(ReservationView reservation);
}
