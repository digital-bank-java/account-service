package com.digitalbank.accountservice.domain.exception;

public class ReservationRequestConflictException extends RuntimeException {

	private final String reservationRequestId;

	public ReservationRequestConflictException(String reservationRequestId) {
		super("Reservation request id was already used with a different payload");
		this.reservationRequestId = reservationRequestId;
	}

	public String reservationRequestId() {
		return reservationRequestId;
	}
}
