package com.digitalbank.accountservice.adapter.out.persistence;

import com.digitalbank.accountservice.application.port.in.ReservationInboxEventView;
import com.digitalbank.accountservice.application.port.in.ReservationReleaseRequestedEvent;
import com.digitalbank.accountservice.application.port.in.ReservationRequestedEvent;
import java.time.Instant;
import java.util.UUID;

final class ReservationCommandInboxJpaMapper {
    private ReservationCommandInboxJpaMapper() {}

    static ReservationCommandInboxJpaEntity toEntity(
            ReservationRequestedEvent event, Instant processedAt, UUID reservationId) {
        return new ReservationCommandInboxJpaEntity(
                event.eventId(),
                event.eventType(),
                event.payloadFingerprint(),
                event.reservationRequestId(),
                event.transactionId(),
                event.correlationId(),
                event.causationId(),
                reservationId,
                processedAt);
    }

    static ReservationCommandInboxJpaEntity toEntity(ReservationReleaseRequestedEvent event, Instant processedAt) {
        return new ReservationCommandInboxJpaEntity(
                event.eventId(),
                event.eventType(),
                event.payloadFingerprint(),
                event.reservationRequestId(),
                event.transactionId(),
                event.correlationId(),
                event.causationId(),
                event.reservationId(),
                processedAt);
    }

    static ReservationInboxEventView toView(ReservationCommandInboxJpaEntity entity) {
        return new ReservationInboxEventView(
                entity.eventId(),
                entity.eventType(),
                entity.payloadFingerprint(),
                entity.reservationRequestId(),
                entity.transactionId(),
                entity.correlationId(),
                entity.causationId(),
                entity.reservationId(),
                entity.processedAt());
    }
}
