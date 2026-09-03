package com.digitalbank.accountservice.application.port.out;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.domain.model.Account;
import java.time.Instant;
import java.util.Optional;

public interface AccountReservationRepository {

    Optional<ReservationView> findByReservationRequestId(String reservationRequestId);

    ReservationView save(ReserveFundsCommand command, Account account, Instant now);
}
