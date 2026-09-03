package com.digitalbank.accountservice.domain.exception;

import java.time.Instant;

public class ReservationExpiredException extends RuntimeException {

    public ReservationExpiredException(String reservationRequestId, Instant expiresAt) {
        super("Reservation " + reservationRequestId + " expired at " + expiresAt);
    }
}
