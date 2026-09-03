package com.digitalbank.accountservice.domain.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String reservationRequestId) {
        super("Reservation not found: " + reservationRequestId);
    }
}
