package com.digitalbank.accountservice.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccountReservationEventOutbox {

	boolean recordIfAbsent(AccountReservationEvent event);

	default List<AccountReservationEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
		return List.of();
	}

	default void markPublished(AccountReservationEvent event, UUID claimToken, Instant publishedAt) {
	}

	default void markFailed(AccountReservationEvent event, UUID claimToken, String error, Instant retryAt) {
	}
}
