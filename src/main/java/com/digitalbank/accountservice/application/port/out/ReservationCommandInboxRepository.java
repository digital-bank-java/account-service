package com.digitalbank.accountservice.application.port.out;

import com.digitalbank.accountservice.application.port.in.ReservationInboxEventView;
import com.digitalbank.accountservice.application.port.in.ReservationReleaseRequestedEvent;
import com.digitalbank.accountservice.application.port.in.ReservationRequestedEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReservationCommandInboxRepository {

    Optional<ReservationInboxEventView> findByEventId(UUID eventId);

    Optional<ReservationInboxEventView> findByReservationRequestId(String reservationRequestId);

    ReservationInboxEventView save(ReservationRequestedEvent event, Instant processedAt);

    default ReservationInboxEventView save(ReservationRequestedEvent event, Instant processedAt, UUID reservationId) {
        return save(event, processedAt);
    }

    ReservationInboxEventView save(ReservationReleaseRequestedEvent event, Instant processedAt);
}
