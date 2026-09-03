package com.digitalbank.accountservice.domain.exception;

public class ReservationEventConflictException extends RuntimeException {

    public ReservationEventConflictException(String message) {
        super(message);
    }
}
