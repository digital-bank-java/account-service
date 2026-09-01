package com.digitalbank.accountservice.application.port.in;

public record ReservationAttempt(
		boolean accepted,
		ReservationView reservation,
		String rejectionCode,
		String rejectionReason) {

	public static ReservationAttempt accepted(ReservationView reservation) {
		return new ReservationAttempt(true, reservation, null, null);
	}

	public static ReservationAttempt rejected(String code, String reason) {
		return new ReservationAttempt(false, null, code, reason);
	}
}
