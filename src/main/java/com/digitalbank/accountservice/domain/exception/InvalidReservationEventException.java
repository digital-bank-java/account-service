package com.digitalbank.accountservice.domain.exception;

public class InvalidReservationEventException extends RuntimeException {

	public InvalidReservationEventException(String message) {
		super(message);
	}

	public InvalidReservationEventException(String message, Throwable cause) {
		super(message, cause);
	}
}
