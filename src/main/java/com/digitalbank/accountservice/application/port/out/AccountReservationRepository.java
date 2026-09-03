package com.digitalbank.accountservice.application.port.out;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.domain.model.Account;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountReservationRepository {

    Optional<ReservationView> findByReservationRequestId(String reservationRequestId);

    default List<ReservationView> findExpiredActiveForUpdate(Instant expiresBy, int limit) {
        throw new UnsupportedOperationException("Expired reservation lookup is not implemented");
    }

    ReservationView save(ReserveFundsCommand command, Account account, Instant now);

    ReservationView save(ReservationView reservation);
}
