package com.digitalbank.accountservice.domain.exception;

import com.digitalbank.accountservice.domain.model.ReservationStatus;

public class ReservationStateConflictException extends RuntimeException {

    public ReservationStateConflictException(String reservationRequestId, ReservationStatus status) {
        super("Reservation " + reservationRequestId + " cannot accept a ledger outcome in status " + status);
    }
}
