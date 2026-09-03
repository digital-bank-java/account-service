package com.digitalbank.accountservice.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReservationInboxEventView(
        UUID eventId,
        String eventType,
        String payloadFingerprint,
        String reservationRequestId,
        UUID transactionId,
        String correlationId,
        String causationId,
        UUID reservationId,
        Instant processedAt) {

    public boolean matches(ReservationRequestedEvent event) {
        return eventId.equals(event.eventId())
                && eventType.equals(event.eventType())
                && payloadFingerprint.equals(event.payloadFingerprint())
                && reservationRequestId.equals(event.reservationRequestId())
                && transactionId.equals(event.transactionId())
                && correlationId.equals(event.correlationId())
                && causationId.equals(event.causationId());
    }

    public boolean matches(ReservationReleaseRequestedEvent event) {
        return eventId.equals(event.eventId())
                && eventType.equals(event.eventType())
                && payloadFingerprint.equals(event.payloadFingerprint())
                && reservationRequestId.equals(event.reservationRequestId())
                && transactionId.equals(event.transactionId())
                && correlationId.equals(event.correlationId())
                && causationId.equals(event.causationId())
                && Objects.equals(reservationId, event.reservationId());
    }
}
