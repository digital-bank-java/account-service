package com.digitalbank.accountservice.application.port.out;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class AccountReservationEventFactory {

	private AccountReservationEventFactory() {
	}

	public static UUID eventId(String eventType, String reservationRequestId) {
		return UUID.nameUUIDFromBytes((eventType + ":" + reservationRequestId).getBytes(StandardCharsets.UTF_8));
	}
}
