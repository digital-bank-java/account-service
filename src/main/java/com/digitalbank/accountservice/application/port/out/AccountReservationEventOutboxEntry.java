package com.digitalbank.accountservice.application.port.out;

import java.util.Objects;

public record AccountReservationEventOutboxEntry(
		AccountReservationEvent event,
		String jsonPayload) {

	public AccountReservationEventOutboxEntry {
		Objects.requireNonNull(event, "Outbox event is required");
		Objects.requireNonNull(jsonPayload, "Outbox JSON payload is required");
	}
}
